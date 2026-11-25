package clean.app.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** The one database of the app on an iPhone: the file at [path] (in the app's Documents folder; stage 3 chooses it), on the bundled SQLite. */
fun CleanDb.Companion.open(path: String): CleanDb = shared {
    Room.databaseBuilder<CleanDb>(path)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*MIGRATIONS)
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
