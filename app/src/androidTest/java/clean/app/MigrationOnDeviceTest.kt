package clean.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import clean.app.data.CleanDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every older database version is brought up to the current one by the app's own migrations, and Room then accepts the result
 * (it checks each table against the entities when it opens a migrated file). Each version's tables are written here by hand from
 * the migrations in Db.kt, since the schemas before version 9 were never exported. Uses its own throwaway file, never `clean.db`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    /** The tables as they were at [version]. */
    private fun create(db: SQLiteDatabase, version: Int) {
        db.execSQL("CREATE TABLE `profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `heightCm` REAL)")
        db.execSQL("CREATE TABLE `workout_row` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `workoutNumber` TEXT NOT NULL, `date` TEXT NOT NULL, `workoutName` TEXT NOT NULL, `durationSeconds` TEXT NOT NULL, `exerciseName` TEXT NOT NULL, `setOrder` TEXT NOT NULL, `weightKg` TEXT NOT NULL, `reps` TEXT NOT NULL, `rpe` TEXT NOT NULL, `distanceMeters` TEXT NOT NULL, `seconds` TEXT NOT NULL, `notes` TEXT NOT NULL, `workoutNotes` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX `index_workout_row_profileId_date` ON `workout_row` (`profileId`, `date`)")
        db.execSQL("CREATE TABLE `measurement_file` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `name` TEXT NOT NULL, `header` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX `index_measurement_file_profileId` ON `measurement_file` (`profileId`)")
        db.execSQL("CREATE TABLE `measurement` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `file` TEXT NOT NULL, `date` TEXT NOT NULL, `value` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX `index_measurement_profileId` ON `measurement` (`profileId`)")
        db.execSQL("CREATE TABLE `kv` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
        // Columns of exercise_setting by the version that added them.
        val columns = buildList {
            add("`profileId` INTEGER NOT NULL"); add("`name` TEXT NOT NULL"); add("`loadType` TEXT NOT NULL"); add("`unit` TEXT")
            if (version >= 2) add("`muscles` TEXT NOT NULL DEFAULT ''")
            if (version >= 4) add("`note` TEXT NOT NULL DEFAULT ''")
            if (version >= 5) add("`overall` TEXT NOT NULL DEFAULT ''")
            if (version >= 6) add("`noteColor` TEXT NOT NULL DEFAULT ''")
            if (version >= 7) { add("`favourite` INTEGER NOT NULL DEFAULT 0"); add("`rest` INTEGER NOT NULL DEFAULT -1") }
        }
        db.execSQL("CREATE TABLE `exercise_setting` (${columns.joinToString()}, PRIMARY KEY(`profileId`, `name`))")
        if (version >= 2) {
            db.execSQL("CREATE TABLE `template` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `name` TEXT NOT NULL, `blocks` TEXT NOT NULL${if (version >= 8) ", `folder` TEXT NOT NULL DEFAULT ''" else ""})")
            db.execSQL("CREATE INDEX `index_template_profileId` ON `template` (`profileId`)")
        }
        db.execSQL("INSERT INTO profile (id, name, unit, heightCm) VALUES (1, 'Me', 'kg', 180.0)")
        db.execSQL("INSERT INTO workout_row (profileId, workoutNumber, date, workoutName, durationSeconds, exerciseName, setOrder, weightKg, reps, rpe, distanceMeters, seconds, notes, workoutNotes) VALUES (1, '1', '2026-01-02 10:00:00', 'Push', '3600', 'Bench Press (Barbell)', '1', '100', '5', '', '', '', '', '')")
        // A setting with its own unit, which version 3 keeps (it drops the ones without).
        db.execSQL("INSERT INTO exercise_setting (profileId, name, loadType, unit) VALUES (1, 'Bench Press (Barbell)', 'WEIGHTED', 'kg')")
        db.execSQL("INSERT INTO kv (`key`, value) VALUES ('active', '1')")
        if (version >= 2) db.execSQL("INSERT INTO template (profileId, name, blocks) VALUES (1, 'Push', '[]')")
        db.version = version
    }

    @Test
    fun everyOlderVersionMigratesToTheCurrentOne() {
        for (version in 1..8) {
            context.deleteDatabase(name)
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { create(it, version) }
            val room = Room.databaseBuilder(context, CleanDb::class.java, name).addMigrations(*CleanDb.MIGRATIONS).build()
            try {
                runBlocking {
                    val dao = room.dao()
                    assertEquals("from $version", 1, dao.profilesNow().size)
                    assertEquals("from $version", "1", dao.get("active"))
                    assertEquals("from $version", 1, room.openHelper.readableDatabase.query("SELECT * FROM workout_row").use { it.count })
                    assertEquals("from $version", 1, room.openHelper.readableDatabase.query("SELECT * FROM exercise_setting").use { it.count })
                    assertEquals("from $version", if (version >= 2) 1 else 0, room.openHelper.readableDatabase.query("SELECT * FROM template").use { it.count })
                }
            } finally {
                room.close()
                context.deleteDatabase(name)
            }
        }
    }
}
