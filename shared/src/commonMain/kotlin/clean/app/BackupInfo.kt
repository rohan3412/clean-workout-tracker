package clean.app

/** What a backup's manifest says (the zip's `clean-backup.json`). */
class BackupManifest(val db: Int, val created: String, val app: String)

/** The counts shown to the user about a database: a backup's, or the phone's own. */
class BackupContents(val profiles: List<String>, val workouts: Int, val first: String?, val last: String?, val weighIns: Int, val templates: Int)

/** A backup that has been looked at without changing anything. */
class BackupInspected(val manifest: BackupManifest, val contents: BackupContents, val animations: Boolean)
