package clean.app.ios

/** What Android keeps in the saved state across the app being killed in the background (the page stack), kept in a small file on the iPhone. */
object IosSavedState {
    val file: String get() = Fs.join(Fs.root, "saved-state.txt")
    private const val SEP = "\u001D"

    fun load(): ArrayList<String>? = Fs.read(file)?.decodeToString()?.takeIf { it.isNotEmpty() }?.split(SEP)?.let { ArrayList(it) }

    fun save(stack: List<String>?) {
        if (stack.isNullOrEmpty()) Fs.delete(file) else Fs.write(file, stack.joinToString(SEP).encodeToByteArray())
    }
}
