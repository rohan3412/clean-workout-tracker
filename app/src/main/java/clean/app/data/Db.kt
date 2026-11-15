package clean.app.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import clean.core.WorkoutRow
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** `kg` or `lb`. Display and entry only; storage is always kg. */
    val unit: String = "kg",
    val heightCm: Double? = null,
)

/** One CSV row, every field as text exactly as imported or as Clean wrote it. */
@Entity(tableName = "workout_row", indices = [Index("profileId", "date")])
data class RowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val workoutNumber: String,
    val date: String,
    val workoutName: String,
    val durationSeconds: String,
    val exerciseName: String,
    val setOrder: String,
    val weightKg: String,
    val reps: String,
    val rpe: String,
    val distanceMeters: String,
    val seconds: String,
    val notes: String,
    val workoutNotes: String,
) {
    fun toRow() = WorkoutRow(
        workoutNumber, date, workoutName, durationSeconds, exerciseName, setOrder,
        weightKg, reps, rpe, distanceMeters, seconds, notes, workoutNotes,
    )

    companion object {
        fun of(profileId: Long, r: WorkoutRow) = RowEntity(
            0, profileId, r.workoutNumber, r.date, r.workoutName, r.durationSeconds, r.exerciseName, r.setOrder,
            r.weightKg, r.reps, r.rpe, r.distanceMeters, r.seconds, r.notes, r.workoutNotes,
        )
    }
}

/** A CSV inside the measurements zip. Row order (by id) is the order of files in the zip. */
@Entity(tableName = "measurement_file", indices = [Index("profileId")])
data class MeasurementFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val header: String,
)

@Entity(tableName = "measurement", indices = [Index("profileId")])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val file: String,
    val date: String,
    /** Original text, float noise included. */
    val value: String,
)

@Entity(tableName = "exercise_setting", primaryKeys = ["profileId", "name"])
data class ExerciseSetting(
    val profileId: Long,
    val name: String,
    /** A [clean.core.LoadType] name. */
    val loadType: String = "NORMAL",
    /** `kg`, `lb`, or null to follow the profile. */
    val unit: String? = null,
    /** A [clean.core.MuscleUse] as text; empty until the user has set or accepted the muscles. */
    @ColumnInfo(defaultValue = "") val muscles: String = "",
    /** A note pinned to the exercise and shown on its card in every workout. Clean's own; it is not written to the CSV. */
    @ColumnInfo(defaultValue = "") val note: String = "",
    /** The overall exercise this one is a variation of; exercises sharing it form a group. Empty when not grouped. */
    @ColumnInfo(defaultValue = "") val overall: String = "",
    /** The colour of the sticky note's band, a name from the app's list; empty for the default. */
    @ColumnInfo(defaultValue = "") val noteColor: String = "",
    /** Starred: shown above everything else in the exercise list and in pickers. */
    @ColumnInfo(defaultValue = "0") val favourite: Boolean = false,
    /** Rest after a set in seconds; -1 follows the app's default, 0 is no timer. */
    @ColumnInfo(defaultValue = "-1") val rest: Int = -1,
    /** What the user is working towards, as `kg x reps` (`100x5`); empty for none. */
    @ColumnInfo(defaultValue = "") val goal: String = "",
)

/** A named list of exercises with planned sets. [blocks] is JSON: `[{"exercise": "...", "sets": ["W", "", "F"]}]`. */
@Entity(tableName = "template", indices = [Index("profileId")])
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val blocks: String,
    /** The folder the template is filed under; empty for none. */
    @ColumnInfo(defaultValue = "") val folder: String = "",
)

/** Small app state: active profile, calendar colours, the workout in progress. */
@Entity(tableName = "kv")
data class Kv(@PrimaryKey val key: String, val value: String)

@Dao
interface CleanDao {
    @Query("SELECT * FROM profile ORDER BY id") fun profiles(): Flow<List<Profile>>
    @Query("SELECT * FROM profile ORDER BY id") suspend fun profilesNow(): List<Profile>
    @Insert suspend fun insert(profile: Profile): Long
    @Update suspend fun update(profile: Profile)
    @Query("DELETE FROM profile WHERE id = :id") suspend fun deleteProfileRow(id: Long)

    // Ordered by date so an older file imported after newer workouts still exports in order;
    // within a workout the id keeps the rows as they were written.
    @Query("SELECT * FROM workout_row WHERE profileId = :p ORDER BY date, id") fun rows(p: Long): Flow<List<RowEntity>>
    @Query("SELECT * FROM workout_row WHERE profileId = :p ORDER BY date, id") suspend fun rowsNow(p: Long): List<RowEntity>
    @Query("SELECT * FROM workout_row WHERE profileId = :p AND date = :date ORDER BY id") suspend fun rowsOn(p: Long, date: String): List<RowEntity>
    @Query("SELECT COUNT(*) FROM workout_row WHERE profileId = :p") suspend fun rowCount(p: Long): Int
    @Query("SELECT COUNT(*) FROM workout_row WHERE profileId = :p AND date LIKE :day || '%'") suspend fun rowCountOn(p: Long, day: String): Int
    @Query("SELECT COUNT(*) FROM measurement WHERE profileId = :p") suspend fun measurementCount(p: Long): Int
    @Insert suspend fun insertRows(rows: List<RowEntity>)
    @Update suspend fun updateRows(rows: List<RowEntity>)
    @Query("DELETE FROM workout_row WHERE profileId = :p AND date = :date") suspend fun deleteWorkout(p: Long, date: String)
    @Query("DELETE FROM workout_row WHERE profileId = :p") suspend fun deleteRows(p: Long)

    @Query("SELECT * FROM measurement_file WHERE profileId = :p ORDER BY id") fun files(p: Long): Flow<List<MeasurementFileEntity>>
    @Query("SELECT * FROM measurement_file WHERE profileId = :p ORDER BY id") suspend fun filesNow(p: Long): List<MeasurementFileEntity>
    @Insert suspend fun insertFiles(files: List<MeasurementFileEntity>)
    @Query("DELETE FROM measurement_file WHERE profileId = :p") suspend fun deleteFiles(p: Long)

    // Newest first, as Strong writes them.
    @Query("SELECT * FROM measurement WHERE profileId = :p ORDER BY date DESC, id") fun measurements(p: Long): Flow<List<MeasurementEntity>>
    @Query("SELECT * FROM measurement WHERE profileId = :p ORDER BY date DESC, id") suspend fun measurementsNow(p: Long): List<MeasurementEntity>
    @Insert suspend fun insertMeasurements(entries: List<MeasurementEntity>)
    @Update suspend fun updateMeasurement(entry: MeasurementEntity)
    @Query("DELETE FROM measurement WHERE id = :id") suspend fun deleteMeasurement(id: Long)
    @Query("DELETE FROM measurement WHERE profileId = :p") suspend fun deleteMeasurements(p: Long)

    @Query("SELECT * FROM exercise_setting WHERE profileId = :p") fun settings(p: Long): Flow<List<ExerciseSetting>>
    @Query("SELECT * FROM exercise_setting WHERE profileId = :p") suspend fun settingsNow(p: Long): List<ExerciseSetting>
    @Upsert suspend fun put(setting: ExerciseSetting)
    @Query("DELETE FROM exercise_setting WHERE profileId = :p") suspend fun deleteSettings(p: Long)
    @Query("DELETE FROM exercise_setting WHERE profileId = :p AND name = :name") suspend fun deleteSetting(p: Long, name: String)

    @Query("SELECT * FROM template WHERE profileId = :p ORDER BY id") fun templates(p: Long): Flow<List<TemplateEntity>>
    @Query("SELECT * FROM template WHERE profileId = :p ORDER BY id") suspend fun templatesNow(p: Long): List<TemplateEntity>
    @Upsert suspend fun put(template: TemplateEntity)
    @Query("DELETE FROM template WHERE id = :id") suspend fun deleteTemplate(id: Long)
    @Query("DELETE FROM template WHERE profileId = :p") suspend fun deleteTemplates(p: Long)

    // Without the workout in progress: it is rewritten on every key press, and nothing that watches the options should stir for that.
    @Query("SELECT * FROM kv WHERE `key` NOT LIKE 'draft:%'") fun kv(): Flow<List<Kv>>
    @Query("SELECT value FROM kv WHERE `key` = :key") suspend fun get(key: String): String?
    @Upsert suspend fun put(kv: Kv)
    @Query("DELETE FROM kv WHERE `key` = :key") suspend fun remove(key: String)
    @Query("DELETE FROM kv WHERE `key` LIKE :prefix || '%'") suspend fun removePrefix(prefix: String)
}

/** The schema version; a backup records it, and one made by a newer schema is refused. */
const val DB_VERSION = 9

@Database(
    entities = [
        Profile::class, RowEntity::class, MeasurementFileEntity::class, MeasurementEntity::class,
        ExerciseSetting::class, Kv::class, TemplateEntity::class,
    ],
    version = DB_VERSION,
    exportSchema = true,
)
abstract class CleanDb : RoomDatabase() {
    abstract fun dao(): CleanDao

    companion object {
        // Version 2: muscles per exercise, and templates.
        private val TO_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_setting ADD COLUMN muscles TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS template (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, profileId INTEGER NOT NULL, name TEXT NOT NULL, blocks TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_template_profileId ON template (profileId)")
            }
        }

        // Version 3: muscles gained a third level and became automatic. Until then every stored
        // assignment was a copy of the old suggestion made by "accept all", so those are dropped
        // and the exercises follow the library again. A row that carries the user's own unit is
        // kept, with only its muscles cleared.
        private val TO_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM exercise_setting WHERE unit IS NULL")
                db.execSQL("UPDATE exercise_setting SET muscles = ''")
            }
        }

        // Version 4: sticky notes.
        private val TO_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_setting ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        // Version 5: variations, as exercises grouped under an overall exercise.
        private val TO_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_setting ADD COLUMN overall TEXT NOT NULL DEFAULT ''")
            }
        }

        // Version 6: a colour per sticky note.
        private val TO_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_setting ADD COLUMN noteColor TEXT NOT NULL DEFAULT ''")
            }
        }

        // Version 7: favourite exercises and a rest length per exercise.
        private val TO_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_setting ADD COLUMN favourite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercise_setting ADD COLUMN rest INTEGER NOT NULL DEFAULT -1")
            }
        }

        // Version 8: template folders.
        private val TO_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE template ADD COLUMN folder TEXT NOT NULL DEFAULT ''")
            }
        }

        // Version 9: a goal per exercise.
        private val TO_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_setting ADD COLUMN goal TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Every step from version 1 up, in order; the migration test runs them from each older version. */
        internal val MIGRATIONS = arrayOf(TO_2, TO_3, TO_4, TO_5, TO_6, TO_7, TO_8, TO_9)

        @Volatile private var instance: CleanDb? = null

        /** Closes the shared database and forgets it, before its file is replaced or deleted (Backup.kt); the app is restarted after. */
        fun close() = synchronized(this) {
            instance?.close()
            instance = null
        }

        /** The one database of the app; the widgets read it too, so it is opened once and shared. */
        fun open(context: Context): CleanDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CleanDb::class.java, "clean.db")
                .addMigrations(*MIGRATIONS).build().also { instance = it }
        }
    }
}
