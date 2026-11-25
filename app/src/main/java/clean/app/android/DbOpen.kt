package clean.app.data

import android.content.Context
import androidx.room.Room

/** The one database of the app, on the phone's own SQLite; the file `clean.db` and its migrations are what every earlier version used. */
fun CleanDb.Companion.open(context: Context): CleanDb = shared {
    Room.databaseBuilder<CleanDb>(context.applicationContext, DB_FILE).addMigrations(*MIGRATIONS).build()
}
