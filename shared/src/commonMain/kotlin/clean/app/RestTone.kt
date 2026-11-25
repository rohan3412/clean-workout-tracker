package clean.app

/** A sound a rest can end with, chosen in Settings; [key] is what is stored (kv `rest.tone`, as 1.0.4 stored it), and Deep bell is used until one is picked. The sound files are in Res.kt. */
enum class RestTone(val key: String, val label: String) {
    Deep("deep", "Deep bell"),
    Bright("bright", "Bell"),
    Gong("gong", "Gong"),
    Soft("soft", "Soft tone"),
    Wood("wood", "Wood block"),
    ;

    companion object {
        fun of(key: String?): RestTone = entries.firstOrNull { it.key == key } ?: Deep
    }
}
