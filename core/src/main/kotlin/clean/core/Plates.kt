package clean.core

/**
 * The bar and plates one has (kv `plates.kg` / `plates.lb`, one per unit): the bar's weight and, per plate size, how many plates are
 * owned in all, or null for as many as needed. Stored as `20|25,20,15:2,10:4` (bar, then sizes, a count after a colon).
 */
data class PlateKit(val bar: Double, val plates: Map<Double, Int?>)

/** A standard bar with standard plates, as many as needed: what the calculator used before plates could be set. */
fun standardKit(unit: String): PlateKit =
    if (unit == "lb") PlateKit(45.0, listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5).associateWith { null })
    else PlateKit(20.0, listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25).associateWith { null })

/** The kit stored for [unit], or the standard one when nothing (or nothing readable) is stored. Sizes are kept largest first. */
fun parsePlateKit(stored: String?, unit: String): PlateKit {
    val parts = stored?.split('|') ?: return standardKit(unit)
    val bar = parts[0].toDoubleOrNull()?.takeIf { it >= 0 } ?: return standardKit(unit)
    val plates = parts.getOrNull(1).orEmpty().split(',').filter { it.isNotBlank() }.mapNotNull { item ->
        val size = item.substringBefore(':').toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        val count = if (':' in item) item.substringAfter(':').toIntOrNull()?.coerceAtLeast(0) ?: return@mapNotNull null else null
        size to count
    }
    return PlateKit(bar, plates.sortedByDescending { it.first }.toMap())
}

/** The stored text for [kit]; null (key removed) when it is the standard one for [unit]. */
fun encodePlateKit(kit: PlateKit, unit: String): String? {
    val sorted = PlateKit(kit.bar, kit.plates.entries.sortedByDescending { it.key }.associate { it.key to it.value })
    if (sorted == standardKit(unit)) return null
    return shortNumber(sorted.bar) + "|" + sorted.plates.entries.joinToString(",") { (size, count) -> shortNumber(size) + (count?.let { ":$it" } ?: "") }
}

/** What goes on each side: plates largest first with how many of each, and what is left over that the kit cannot make (0 when exact). */
data class PlateLoad(val perSide: List<Pair<Double, Int>>, val leftOver: Double)

/**
 * The plates for [total] on [kit]'s bar, the same on each side, largest first, never more of a size than half the plates owned. Null
 * when [total] is less than the bar. Plates owned in odd numbers leave the odd one out.
 */
fun loadPlates(total: Double, kit: PlateKit): PlateLoad? {
    var side = (total - kit.bar) / 2
    if (side < -1e-9) return null
    val used = mutableListOf<Pair<Double, Int>>()
    for ((plate, owned) in kit.plates.entries.sortedByDescending { it.key }) {
        val fit = Math.floor(side / plate + 1e-9).toInt()
        val n = if (owned == null) fit else minOf(fit, owned / 2)
        if (n > 0) {
            used += plate to n
            side -= n * plate
        }
    }
    return PlateLoad(used, if (side > 0.005) Math.round(side * 2 * 100) / 100.0 else 0.0)
}
