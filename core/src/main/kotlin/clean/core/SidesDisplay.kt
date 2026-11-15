package clean.core

/*
 * Left and right, as a finished workout shows it (docs/left-right.md, "Showing it"): the best round of a block, a
 * round as one line of text, and how far the lagging side was behind. Pure, so the screens only lay them out.
 */

/**
 * The comparable best round: the one whose weaker side is best (a round with a side missing or only a warm-up
 * ranks below any complete one), ties to the heavier total, then to more reps. Null when no round has a working set.
 */
fun bestRound(rounds: List<Round>): Round? {
    fun figure(set: SetItem?): Double? = set?.takeIf { it.type != SetType.WARMUP }?.let { (it.weight ?: 0.0) * (1 + (it.reps ?: 0) / 30.0) }
    fun reps(set: SetItem?): Int = set?.takeIf { it.type != SetType.WARMUP }?.reps ?: 0
    return rounds.filter { figure(it.left) != null || figure(it.right) != null }.maxWithOrNull(
        compareBy<Round> { minOf(figure(it.left) ?: -1.0, figure(it.right) ?: -1.0) }
            .thenBy { (figure(it.left) ?: 0.0) + (figure(it.right) ?: 0.0) }
            .thenBy { reps(it.left) + reps(it.right) },
    )
}

/**
 * The kind a round has as a whole: the kind both halves share, or null when they differ (each half then shows its own).
 * A round with one half takes that half's kind.
 */
fun roundKind(left: SetType?, right: SetType?): SetType? = if (left == null || right == null || left == right) left ?: right else null

/** Whether a round takes a number: unless every half it has is a warm-up or a drop set ([numbered]). */
fun roundCounts(left: SetType?, right: SetType?): Boolean = listOfNotNull(left, right).any { it.numbered }

/**
 * What each of [rounds] is called on a page: the letter of its kind (`W`, `F`, `D`) or its number among the rounds
 * that are not warm-ups or drop sets, as the logging screen numbers them (a failure round takes a number's place but shows its letter).
 * A round whose halves differ in kind is called by its number (a warm-up with a working half counts as working); each half's
 * own letter is shown beside its value ([halfLetter]).
 */
fun roundLabels(rounds: List<Round>): List<String> {
    var number = 0
    return rounds.map { round ->
        val left = round.left?.type
        val right = round.right?.type
        if (roundCounts(left, right)) number++
        val kind = roundKind(left, right)
        if (kind == null || kind == SetType.NORMAL) number.toString() else kind.letter
    }
}

/** The kind's letter to show beside [side]'s value when the round's halves differ in kind (`N` for a normal set), else null. */
fun halfLetter(round: Round, side: Side): String? {
    val left = round.left?.type
    val right = round.right?.type
    if (roundKind(left, right) != null) return null
    return (if (side == Side.LEFT) left else right)?.letter?.ifEmpty { "N" }
}

/** A round as one line, `L 20 kg × 12 · R 20 kg × 10`, a half that was not done as `–`; [describe] writes one set. */
fun roundLine(round: Round, describe: (SetItem) -> String): String =
    "L ${round.left?.let(describe) ?: "–"} · R ${round.right?.let(describe) ?: "–"}"

/**
 * How far one side lagged in a session: [percent] is the whole-percent gap of the measure that decided it, and
 * [reps] the gap in reps when that measure was reps ([byReps]). The measure is the one [Imbalance.lagging] is read from.
 */
data class ImbalanceNote(val lagging: Side, val percent: Int, val reps: Int?, val byReps: Boolean)

/** The note for [imbalance], or null when the sides were equal (or there is nothing to compare). */
fun imbalanceNote(imbalance: Imbalance): ImbalanceNote? {
    val side = imbalance.lagging ?: return null
    // The same order [imbalance] reads its lagging side in: 1RM, volume, then reps.
    val deciding = listOf(imbalance.oneRm to false, imbalance.volume to false, imbalance.reps to true)
        .firstOrNull { it.first != null && it.first!!.difference != 0.0 } ?: return null
    val gap = deciding.first!!
    val percent = Math.round(Math.abs(gap.percent)).toInt().coerceAtLeast(1)
    return ImbalanceNote(side, percent, if (deciding.second) Math.round(Math.abs(gap.difference)).toInt() else null, deciding.second)
}

/** The short caption over a block: `Right −2 reps`, `Left −7 %`. */
fun ImbalanceNote.caption(): String =
    (if (lagging == Side.LEFT) "Left" else "Right") + " −" + (if (byReps && reps != null) (if (reps == 1) "1 rep" else "$reps reps") else "$percent %")

/** The summary's sentence for [exercise]: `Bicep Curl: right lagged by 2 reps (−17 %)`. */
fun ImbalanceNote.sentence(exercise: String): String =
    "$exercise: ${if (lagging == Side.LEFT) "left" else "right"} lagged by " +
        (if (byReps && reps != null) (if (reps == 1) "1 rep" else "$reps reps") + " (−$percent %)" else "$percent %")

/** The imbalance of one finished [block], read like an exercise's session ([imbalance]); null unless it is paired and its sides differ. */
fun imbalanceOf(
    block: Block, date: String, rule: OneRm, loadType: LoadType = LoadType.NORMAL, bodyweightAt: (String) -> Double? = { null },
): ImbalanceNote? {
    if (!block.paired) return null
    val progress = progressBySide(listOf(Session(date, block.sets, block.setSides)), rule, loadType, bodyweightAt).single()
    return imbalance(progress)?.let(::imbalanceNote)
}

/** The two sides' values as one quiet line, `L 100 kg · R 92 kg`; a side with none is a dash, and null when neither has one. */
fun sidesLine(left: String?, right: String?): String? =
    if (left == null && right == null) null else "L ${left ?: "–"} · R ${right ?: "–"}"

/** The same line said in words, for a screen reader: `Left 100 kg, right 92 kg`. */
fun sidesSpoken(left: String?, right: String?): String? =
    if (left == null && right == null) null else "Left ${left ?: "not done"}, right ${right ?: "not done"}"

/** The gap in full: `Right −2 reps · −17 %` where reps decided it (the percentage follows), else [caption] (`Left −7 %`). */
fun ImbalanceNote.detail(): String = if (byReps && reps != null) caption() + " · −$percent %" else caption()

/**
 * The latest session of [sessions] with a left and right block whose sides can be compared: its date and the gap, the
 * gap null when the sides were even. Null when no session has both sides to compare.
 */
fun latestImbalance(
    sessions: List<Session>, rule: OneRm, loadType: LoadType = LoadType.NORMAL, bodyweightAt: (String) -> Double? = { null },
): Pair<String, ImbalanceNote?>? {
    for (progress in progressBySide(sessions, rule, loadType, bodyweightAt).asReversed()) {
        val gap = imbalance(progress) ?: continue
        if (gap.oneRm == null && gap.reps == null && gap.volume == null) continue
        return progress.date to imbalanceNote(gap)
    }
    return null
}

/** How many rounds of left and right work [sessions] hold, as counted (a round is 1, a lone side half; warm-ups left out). */
fun roundsLogged(sessions: List<Session>): Double =
    sessions.sumOf { s -> s.sets.indices.count { s.sideOf(it) != null && s.sets[it].type != SetType.WARMUP } * 0.5 }

/** `1 round`, `2.5 rounds`. */
fun roundsText(count: Double): String = "${shortNumber(count)} ${if (count == 1.0) "round" else "rounds"}"

/** What a side leads or lags by in a set count, as the Body tab's Sides view reads it: the lagging side and its shortfall in whole percent of the larger side. */
data class SideShortfall(val lagging: Side, val percent: Int)

/** The sides' shortfall: null when neither has credit or they are even to the whole percent. */
fun SideCredit.shortfall(): SideShortfall? {
    val larger = maxOf(left, right)
    if (larger <= 0.0) return null
    val percent = Math.round(Math.abs(left - right) / larger * 100).toInt()
    return if (percent == 0) null else SideShortfall(if (right < left) Side.RIGHT else Side.LEFT, percent)
}

/** The shortfall as one short phrase: `R −25 %`, `L −8 %`, or `even`. */
fun SideShortfall?.phrase(): String = if (this == null) "even" else (if (lagging == Side.LEFT) "L" else "R") + " −" + percent + " %"

/** The shortfall in words for a screen reader: `right 25 percent less`, or `even`. */
fun SideShortfall?.spoken(): String = if (this == null) "even" else (if (lagging == Side.LEFT) "left" else "right") + " " + percent + " percent less"

/**
 * The estimated 1RM of each side of a left and right [block] done on [date], left then right, for History's two columns. Each side
 * counts its own working sets ([Session.forSide], [progressBySide]); a side with no estimate (timed, no reps, a half not done) is null.
 */
fun sideOneRms(date: String, block: Block, rule: OneRm, loadType: LoadType = LoadType.NORMAL, bodyweightAt: (String) -> Double? = { null }): Pair<Double?, Double?> {
    val sided = progressBySide(listOf(Session(date, block.sets, block.setSides)), rule, loadType, bodyweightAt).first()
    return sided.left?.estimated1Rm to sided.right?.estimated1Rm
}
