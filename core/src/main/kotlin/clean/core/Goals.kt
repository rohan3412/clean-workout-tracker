package clean.core

/** A goal as stored (`100.0x5`, kilos then reps) read back, or null when there is none. */
fun parseGoal(text: String?): Pair<Double, Int>? {
    val parts = text?.split('x') ?: return null
    val kg = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
    val reps = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return kg to reps
}

/**
 * The estimated 1RM a goal of [kg] for [reps] stands for, on the real load at [bodyweight]. Null when the
 * reps are past the rule's limit: such a goal has no estimate and is met by a set of that weight or more.
 */
fun goalOneRm(kg: Double, reps: Int, rule: OneRm, loadType: LoadType, bodyweight: Double?): Double? {
    if (reps > rule.maxReps) return null
    val load = realLoad(loadType, kg, bodyweight) ?: kg
    return estimate1Rm(load, reps, rule) ?: load
}

/**
 * The date of the first session that met the goal, or null if none has. Up to the rule's rep limit a
 * session meets it when its best estimated 1RM reaches the goal's (each session on its own weigh-in);
 * above it, when a set of at least the goal's weight (at most, for an assisted lift) had at least its reps.
 * Warm-ups never count. A timed or cardio exercise has no goal here. In a session with left and right sets
 * both sides must meet it (the weaker side's figure, as [recordsOf] gives).
 */
fun goalReachedOn(sessions: List<Session>, kg: Double, reps: Int, rule: OneRm, loadType: LoadType, bodyweightAt: (String) -> Double?): String? {
    if (loadType == LoadType.TIMED || loadType == LoadType.CARDIO) return null
    for (session in sessions) {
        val target = goalOneRm(kg, reps, rule, loadType, bodyweightAt(session.date))
        val met = if (target == null) (if (session.paired) Side.values().map { session.forSide(it) } else listOf(session)).all { series ->
            series.sets.any {
                it.type != SetType.WARMUP && it.weight != null && (it.reps ?: 0) >= reps &&
                    (if (loadType == LoadType.ASSISTED) it.weight <= kg else it.weight >= kg)
            }
        } else (recordsOf(listOf(session), rule, loadType, bodyweightAt).estimated1Rm?.value ?: 0.0) >= target - 1e-9
        if (met) return session.date
    }
    return null
}
