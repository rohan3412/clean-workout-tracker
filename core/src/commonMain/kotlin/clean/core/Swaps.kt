package clean.core

/** The equipment a built-in-style name carries in its last brackets, lower-cased: `Bench Press (Dumbbell)` is `dumbbell`. Null when it has none. */
fun bracketEquipment(exercise: String): String? {
    val open = exercise.lastIndexOf('(')
    if (open < 0 || !exercise.endsWith(")")) return null
    return exercise.substring(open + 1, exercise.length - 1).trim().lowercase().ifEmpty { null }
}

/**
 * Exercises worth swapping [current] for, best first: those that train a main muscle it trains. They rank by how much of the main
 * muscles they share, then by whether the user has done them ([done] is how many times each has been logged), and a little for equipment
 * the user has used on anything. Nothing is suggested when [current] has no main muscle known.
 */
fun swapSuggestions(
    current: String,
    candidates: Collection<String>,
    musclesOf: (String) -> MuscleUse,
    done: Map<String, Int>,
    limit: Int = 8,
): List<String> {
    val mine = musclesOf(current)
    if (mine.primary.isEmpty()) return emptyList()
    val equipment = done.filterValues { it > 0 }.keys.mapNotNull(::bracketEquipment).toSet()
    class Scored(val name: String, val score: Double, val times: Int)
    val scored = candidates.distinct().mapNotNull { name ->
        if (name == current) return@mapNotNull null
        val use = musclesOf(name)
        val shared = mine.primary.intersect(use.primary).size
        if (shared == 0) return@mapNotNull null
        val same = shared.toDouble() / mine.primary.union(use.primary).size
        val helpers = mine.secondary.intersect(use.secondary + use.primary).size.toDouble() / maxOf(1, mine.secondary.size)
        val times = done[name] ?: 0
        val score = same * 100 + helpers * 10 + (if (times > 0) 30 + minOf(times, 10) else 0) + (if (bracketEquipment(name) in equipment) 15 else 0)
        Scored(name, score, times)
    }
    return scored.sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.times }.thenBy { it.name }).take(limit).map { it.name }
}
