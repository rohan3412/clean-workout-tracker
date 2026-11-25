package clean.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.DB_VERSION
import clean.app.data.Repo
import clean.app.data.open
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The phone's database must open, rows intact, with the code that moved to the shared module (Room for Kotlin Multiplatform).
 * The file is built the way the old code's Room built it: by hand, from the exported schema of the current version (shared/schemas),
 * tables, indices, Room's master table with its identity hash, `user_version`, and then rows. Opening it must neither migrate nor
 * recreate anything (Room refuses a database whose hash differs), and every row must read back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DbContinuityTest {
    @Test fun `a database made by the previous code opens with its rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema = JSONObject(File("../shared/schemas/clean.app.data.CleanDb/$DB_VERSION.json").readText()).getJSONObject("database")
        assertEquals(DB_VERSION, schema.getInt("version"))

        CleanDb.close()
        val file = context.getDatabasePath("clean.db").apply { parentFile!!.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO profile (id, name, unit, heightCm) VALUES (1, 'Rohan', 'kg', 181.5)")
            db.execSQL(
                "INSERT INTO workout_row (id, profileId, workoutNumber, date, workoutName, durationSeconds, exerciseName, setOrder, weightKg, reps, rpe, distanceMeters, seconds, notes, workoutNotes) " +
                    "VALUES (7, 1, '3', '2026-01-02 10:00:00', 'Push ', '3600', 'Bench Press (Barbell) ', '1', '100.5', '5', '', '0', '0', 'note  ', '')",
            )
            db.execSQL("INSERT INTO kv (`key`, `value`) VALUES ('draft:workout:1', '{\"name\":\"Push\",\"start\":1}')")
            db.execSQL("INSERT INTO template (id, profileId, name, blocks, folder) VALUES (2, 1, 'Push', '[{\"exercise\":\"Squat\",\"sets\":[\"N\"]}]', 'Split')")
            db.version = DB_VERSION
        }

        val db = CleanDb.open(context)
        runBlocking {
            val dao = db.dao()
            val profile = dao.profilesNow().single()
            assertEquals("Rohan", profile.name); assertEquals(181.5, profile.heightCm!!, 0.0)
            val row = dao.rowsNow(1).single()
            assertEquals(7L, row.id); assertEquals("Push ", row.workoutName); assertEquals("Bench Press (Barbell) ", row.exerciseName)
            assertEquals("100.5", row.weightKg); assertEquals("note  ", row.notes)
            assertEquals("{\"name\":\"Push\",\"start\":1}", dao.get("draft:workout:1"))
            // A write goes through, and reads back, on the reopened file.
            val repo = Repo(db)
            repo.createProfile("Second")
            assertEquals(listOf("Rohan", "Second"), dao.profilesNow().map { it.name })
        }
        CleanDb.close()
        // The same file again, opened afresh: nothing was lost on the way out.
        runBlocking { assertEquals(2, CleanDb.open(context).dao().profilesNow().size) }
        CleanDb.close()
    }
}
