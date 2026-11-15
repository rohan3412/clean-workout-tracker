package clean.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramTest {
    private val start = LocalDate.parse("2025-01-06") // a Monday
    private val later = start.plusWeeks(10)
    private val same: (String) -> String = { it }

    private fun ids(prefix: String, n: Int) = (1..n).map { "$prefix$it" }
    private val push = ids("p", 8)
    private val pull = ids("l", 8)
    private val legs = ids("g", 6)
    private val pushDay = ProgramDay("Push", 1, push)
    private val pullDay = ProgramDay("Pull", 2, pull)
    private val legsDay = ProgramDay("Legs", 3, legs)

    /** Push on Monday, Pull on Tuesday, Legs on Thursday, the rest are rest days. */
    private val plan = listOf(pushDay, pullDay, null, legsDay, null, null, null)
    private val version = ProgramVersion(start, plan)

    private fun workout(date: LocalDate, name: String, exercises: List<String>, time: String = "10:00:00"): Workout {
        val d = "$date $time"
        val blocks = exercises.map { e -> Block(e, listOf(SetItem(WorkoutRow("1", d, name, "60", e, "1", "50.0", "5", "", "0.0", "0.0", "", ""), SetType.NORMAL))) }
        return Workout("1", d, name, 60, blocks)
    }

    /** The plan done on its own days in week [i] from [start]. */
    private fun fullWeek(i: Int): List<Workout> {
        val monday = start.plusWeeks(i.toLong())
        return listOf(workout(monday, "Push", push), workout(monday.plusDays(1), "Pull", pull), workout(monday.plusDays(3), "Legs", legs))
    }

    private fun weeks(ws: List<Workout>, today: LocalDate = later, versions: List<ProgramVersion> = listOf(version)) = programWeeks(versions, ws, today, same)

    private fun day(n: Int) = ProgramDay("D", 9, ids("e", n))

    @Test
    fun `an exact match`() {
        val m = matchDay(pushDay, push.toSet())
        assertEquals(DayMatch(8, 0, 0, 8), m)
        assertTrue(m.matches)
    }

    @Test
    fun `one added and one removed of eight still match`() {
        val m = matchDay(pushDay, push.drop(1).toSet() + "extra")
        assertEquals(DayMatch(7, 1, 1, 8), m)
        assertTrue(m.matches)
    }

    @Test
    fun `two added of eight do not`() {
        assertFalse(matchDay(pushDay, push.toSet() + "x" + "y").matches)
        assertFalse(matchDay(pushDay, push.drop(2).toSet()).matches)
    }

    @Test
    fun `twelve exercises allow two`() {
        val d = day(12)
        assertTrue(matchDay(d, d.exercises.drop(2).toSet() + "x" + "y").matches)
        assertFalse(matchDay(d, d.exercises.drop(3).toSet() + "x" + "y" + "z").matches)
        // 0.1 * 30 is a hair over 3 in floating point and must not round up to 4.
        val big = day(30)
        assertTrue(matchDay(big, big.exercises.drop(3).toSet() + "x" + "y" + "z").matches)
        assertFalse(matchDay(big, big.exercises.drop(4).toSet() + "w" + "x" + "y" + "z").matches)
    }

    @Test
    fun `a variation swap is no change`() {
        val of = { name: String -> if (name == "bench-db") "bench" else name }
        val day = ProgramDay("Push", 1, listOf("bench", "fly"))
        val w = workout(start, "Push", listOf("bench-db", "fly"))
        assertEquals(DayMatch(2, 0, 0, 2), matchDay(day, workoutOveralls(w, of)))
    }

    @Test
    fun `a one-exercise day does not match a different one-exercise workout`() {
        val m = matchDay(ProgramDay("Curl", 1, listOf("curl")), setOf("squat"))
        assertEquals(DayMatch(0, 1, 1, 1), m)
        assertFalse(m.matches)
        assertTrue(matchDay(ProgramDay("Curl", 1, listOf("curl")), setOf("curl")).matches)
    }

    @Test
    fun `duplicates and split runs count once`() {
        val of = { name: String -> if (name.startsWith("row")) "row" else name }
        val w = workout(start, "Pull", listOf("row-a", "curl", "row-b", "row-a"))
        assertEquals(setOf("row", "curl"), workoutOveralls(w, of))
        // A card with no set at all (a note only) is no exercise.
        val noted = Workout("1", "$start 10:00:00", "x", 60, listOf(Block("curl", emptyList())))
        assertEquals(emptySet(), workoutOveralls(noted, same))
    }

    @Test
    fun `days done in a different order still make the week`() {
        val monday = start
        val ws = listOf(workout(monday, "Pull", pull), workout(monday.plusDays(1), "Push", push), workout(monday.plusDays(3), "Legs", legs))
        val w = weeks(ws).first()
        assertTrue(w.met)
        assertEquals(listOf(SlotState.MOVED, SlotState.MOVED, SlotState.REST_KEPT, SlotState.HIT), w.slots.take(4).map { it.state })
        assertEquals(monday.plusDays(1).toString(), w.slots[0].workout!!.take(10))
        assertEquals(monday.plusDays(3), w.metOn)
    }

    @Test
    fun `a missing training day is not met`() {
        val w = weeks(fullWeek(0).dropLast(1)).first()
        assertFalse(w.met)
        assertNull(w.metOn)
        assertEquals(SlotState.MISSED, w.slots[3].state)
    }

    @Test
    fun `extra workouts are ignored and two on one day can satisfy two days`() {
        val monday = start
        val ws = listOf(
            workout(monday, "Push", push, "08:00:00"), workout(monday, "Pull", pull, "18:00:00"),
            workout(monday.plusDays(2), "Other", listOf("x1", "x2")), workout(monday.plusDays(3), "Legs", legs),
        )
        val w = weeks(ws).first()
        assertTrue(w.met)
        assertEquals(SlotState.HIT, w.slots[0].state)
        assertEquals(SlotState.MOVED, w.slots[1].state)
        // The workout on the rest day is shown for it and changes nothing.
        assertEquals(SlotState.REST_TRAINED, w.slots[2].state)
    }

    @Test
    fun `near-identical days need two different workouts`() {
        val a = ProgramDay("Upper A", 1, ids("a", 8))
        val b = ProgramDay("Upper B", 2, ids("a", 7) + "b8")
        val v = ProgramVersion(start, listOf(a, null, b, null, null, null, null))
        val one = listOf(workout(start, "Upper A", a.exercises))
        assertFalse(weeks(one, versions = listOf(v)).first().met)
        val two = one + workout(start.plusDays(2), "Upper B", b.exercises)
        val w = weeks(two, versions = listOf(v)).first()
        assertTrue(w.met)
        assertEquals(start.toString(), w.slots[0].workout!!.take(10))
        assertEquals(start.plusDays(2).toString(), w.slots[2].workout!!.take(10))
    }

    @Test
    fun `a rest day trained does not change whether the week is met`() {
        val kept = weeks(fullWeek(0)).first()
        val trained = weeks(fullWeek(0) + workout(start.plusDays(5), "Run", listOf("run"))).first()
        assertTrue(kept.met && trained.met)
        assertEquals(SlotState.REST_KEPT, kept.slots[5].state)
        assertEquals(SlotState.REST_TRAINED, trained.slots[5].state)
    }

    @Test
    fun `the week in progress is never a miss`() {
        val thursday = start.plusDays(3)
        val partial = weeks(fullWeek(0).take(1), thursday).single()
        assertTrue(partial.current)
        assertFalse(partial.met)
        assertEquals(SlotState.UPCOMING, partial.slots[3].state)
        assertEquals(WeekState.IN_PROGRESS, replayStreak(listOf(partial.toMet())).states.single())
        val done = weeks(fullWeek(0), thursday).single()
        assertTrue(done.met && done.current)
        assertEquals(1, replayStreak(listOf(done.toMet())).length)
        // A future day of the current week is upcoming, a rest day too.
        assertEquals(SlotState.UPCOMING, partial.slots[4].state)
    }

    @Test
    fun `weeks before the first version are not scored and an earlier week keeps its result when the plan changes`() {
        val ws = (0 until 6).flatMap { fullWeek(it) }
        val v2 = ProgramVersion(start.plusWeeks(4), listOf(legsDay, null, null, null, null, null, null))
        val before = weeks(ws)
        val after = weeks(ws, versions = listOf(version, v2))
        assertEquals(before.take(4), after.take(4))
        // From week 4 the new plan wants only Legs, which fullWeek has on Thursdays.
        assertTrue(after[4].met)
        assertEquals(SlotState.MOVED, after[4].slots[0].state)
        // A plan that starts in week 3 does not score weeks 0 to 2.
        val late = weeks(ws, versions = listOf(ProgramVersion(start.plusWeeks(3), plan)))
        assertEquals(start.plusWeeks(3), late.first().monday)
        assertEquals(8, late.size)
    }

    @Test
    fun `the streak and the shield come from the shared code`() {
        val ws = (0..7).flatMap { fullWeek(it) } + fullWeek(9)
        val standing = programStanding(listOf(version), ws, start.plusWeeks(10), same)
        assertEquals(11, standing.weeks.size)
        assertEquals(9, standing.streak.length)
        assertFalse(standing.streak.shield)
        assertEquals(listOf(start.plusWeeks(8)), standing.streak.forgiven)
        assertEquals(WeekState.IN_PROGRESS, standing.streak.states.last())
        val badge = standing.awards.first { it.id == "program-4" }
        assertEquals("1 month on program", badge.title)
        assertEquals(Ladder.PROGRAM, badge.ladder)
        assertNotNull(badge.earned)
        assertNull(standing.awards.first { it.id == "program-26" }.earned)
        assertEquals(listOf("program-4", "program-13", "program-26", "program-52"), standing.awards.map { it.id })
        val next = nextMilestones(standing.awards, standing.streak.length, 0, standing.streak.length)
        assertEquals("program-13", next.single { it.award.ladder == Ladder.PROGRAM }.award.id)
    }

    @Test
    fun `the first week this plan fits`() {
        val ws = (2..6).flatMap { fullWeek(it) } + fullWeek(0).take(2)
        assertEquals(start.plusWeeks(2), firstFittingWeek(plan, ws, start.plusWeeks(7), same))
        assertNull(firstFittingWeek(plan, (2..5).flatMap { fullWeek(it) }, start.plusWeeks(7), same))
        assertNull(firstFittingWeek(plan, emptyList(), start.plusWeeks(7), same))
        // Not looking back past the first workout.
        assertEquals(start, firstFittingWeek(plan, (0..6).flatMap { fullWeek(it) }, start.plusWeeks(7), same))
    }

    @Test
    fun `today on the program`() {
        val monday = start.plusWeeks(7)
        // Monday, nothing done: Push is today's, the others follow.
        val fresh = todayPlan(listOf(version), emptyList(), monday, same)!!
        assertFalse(fresh.done)
        assertEquals(listOf("Push" to PickKind.TODAY, "Pull" to PickKind.LATER, "Legs" to PickKind.LATER), fresh.candidates.map { it.day.label to it.kind })
        // Wednesday is rest: Push and Pull are still to do from earlier, then Legs.
        val wed = todayPlan(listOf(version), emptyList(), monday.plusDays(2), same)!!
        assertNull(wed.today.day)
        assertEquals(listOf(PickKind.EARLIER, PickKind.EARLIER, PickKind.LATER), wed.candidates.map { it.kind })
        // Monday's Push done: today is done and Pull comes next.
        val done = todayPlan(listOf(version), listOf(workout(monday, "Push", push)), monday, same)!!
        assertTrue(done.done)
        assertEquals("Pull", done.candidates.first().day.label)
        // Everything done by Friday: next week's first training day.
        val all = todayPlan(listOf(version), fullWeek(7), monday.plusDays(4), same)!!
        assertEquals(listOf(Pick(pushDay, 0, monday.plusWeeks(1), PickKind.NEXT_WEEK)), all.candidates)
        // Before the program starts there is nothing.
        assertNull(todayPlan(listOf(ProgramVersion(start.plusWeeks(20), plan)), emptyList(), monday, same))
    }

    @Test
    fun `what a finished workout did for the program`() {
        val ws = fullWeek(0)
        val last = programOutcome(listOf(version), ws, ws.last(), later, same)!!
        assertEquals("Legs", last.label)
        assertEquals(0, last.different)
        assertTrue(last.weekCompleted)
        assertFalse(programOutcome(listOf(version), ws, ws.first(), later, same)!!.weekCompleted)
        val changed = ws.dropLast(1) + workout(start.plusDays(3), "Legs", legs.drop(1) + "g9")
        assertEquals(1, programOutcome(listOf(version), changed, changed.last(), later, same)!!.different)
        assertNull(programOutcome(listOf(version), ws + workout(start.plusDays(4), "Run", listOf("run")), ws[0].copy(date = "$start 11:11:11"), later, same))
        // The workout that reaches four weeks earns the badge.
        val month = (0..3).flatMap { fullWeek(it) }
        val earned = programOutcome(listOf(version), month, month.last(), start.plusWeeks(4), same)!!
        assertEquals(listOf("program-4"), earned.awards.map { it.id })
    }

    @Test
    fun `how long a streak is said`() {
        assertEquals("0 weeks", spanText(0))
        assertEquals("1 week", spanText(1))
        assertEquals("3 weeks", spanText(3))
        assertEquals("1 month", spanText(4))
        assertEquals("1 month, 1 week", spanText(5))
        assertEquals("3 months", spanText(13))
        // Not yet the 3 months the ladder gives at 13 weeks.
        assertEquals("2 months, 3 weeks", spanText(12))
        assertEquals("5 months, 3 weeks", spanText(25))
        assertEquals("6 months", spanText(26))
        assertEquals("1 year", spanText(52))
        assertEquals("1 year, 1 month", spanText(56))
        assertEquals(5, approxMonths(25))
        assertEquals(6, approxMonths(26))
        assertEquals(12, approxMonths(52))
    }

    @Test
    fun `a program survives being stored with awkward names`() {
        val odd = "Push; | , \\ \n\r day, ;|"
        val a = ProgramVersion(start, listOf(ProgramDay(odd, 42, listOf("Bench; Press", "A|B", "Fly,\nX", "")), null, ProgramDay("", 7, emptyList()), null, null, null, null))
        val b = ProgramVersion(start.plusWeeks(4), listOf(null, null, null, null, null, null, ProgramDay("Run", 1, listOf("run"))))
        assertEquals(listOf(a, b), decodeProgram(encodeProgram(listOf(b, a))))
        assertEquals(emptyList(), decodeProgram(null))
        assertEquals(emptyList(), decodeProgram(""))
        assertEquals(listOf(b), decodeProgram("rubbish;" + encodeProgram(listOf(b)) + ";2025-01-01|x"))
        // A version dated like an earlier one replaces it.
        val c = a.copy(days = b.days)
        assertEquals(listOf(c), withVersion(listOf(a), c))
        assertEquals(listOf(a.copy(days = plan), b), withVersion(listOf(a, b), a.copy(days = plan)))
    }

    private fun ref(id: Long, name: String, folder: String = "") = TemplateRef(id, name, folder)
    private fun done(day: String, name: String) = workout(LocalDate.parse(day), name, listOf("x"))

    @Test
    fun `the next template follows the current split`() {
        val today = LocalDate.parse("2025-03-10")
        val templates = listOf(ref(1, "Push", CURRENT_SPLIT_NAME), ref(2, "Pull", CURRENT_SPLIT_NAME.lowercase()), ref(3, "Legs", CURRENT_SPLIT_NAME), ref(4, "Arms"))
        // Legs was never done: first.
        val ws = listOf(done("2025-03-04", "Pull"), done("2025-03-06", "Push"), done("2025-03-08", "Arms"))
        val next = nextTemplate(templates, ws, CURRENT_SPLIT_NAME, today)!!
        assertEquals(3L, next.template.id)
        assertEquals("never done", next.reason)
        assertTrue(next.fromSplit)
        // All done: the one longest ago.
        val all = ws + done("2025-03-09", "Legs")
        val longest = nextTemplate(templates, all, CURRENT_SPLIT_NAME, today)!!
        assertEquals(2L, longest.template.id)
        assertEquals("last done 6 days ago", longest.reason)
        // Skipping passes it over, and skipping all comes round again.
        assertEquals(1L, nextTemplate(templates, all, CURRENT_SPLIT_NAME, today, setOf(2L))!!.template.id)
        assertEquals(2L, nextTemplate(templates, all, CURRENT_SPLIT_NAME, today, setOf(1L, 2L, 3L))!!.template.id)
        // The split empty: every template that has been used.
        val loose = listOf(ref(1, "Push"), ref(2, "Pull"), ref(5, "Never used"))
        val fallback = nextTemplate(loose, ws, CURRENT_SPLIT_NAME, today)!!
        assertEquals(2L, fallback.template.id)
        assertEquals("last done 6 days ago", fallback.reason)
        assertFalse(fallback.fromSplit)
        assertEquals("last done 2 days ago", nextTemplate(listOf(ref(1, "Arms")), ws, CURRENT_SPLIT_NAME, today)!!.reason)
        assertNull(nextTemplate(loose, emptyList(), CURRENT_SPLIT_NAME, today))
        assertNull(nextTemplate(emptyList(), ws, CURRENT_SPLIT_NAME, today))
    }

    // --- The first plan ---

    private val sToday = LocalDate.parse("2025-03-12") // a Wednesday; the weeks looked at start on 2025-01-13
    private val sMonday = LocalDate.parse("2025-03-10")
    private fun st(id: Long, name: String, exercises: List<String>, folder: String = "") = SuggestTemplate(ref(id, name, folder), exercises)
    private fun weekDay(week: Int, dayIndex: Int) = sMonday.minusWeeks(week.toLong()).plusDays(dayIndex.toLong())
    private val upper = ids("u", 8)
    private val lower = ids("w", 8)

    @Test
    fun `a Monday and Thursday routine gives those two days`() {
        val ws = (1..8).flatMap { listOf(workout(weekDay(it, 0), "Upper", upper), workout(weekDay(it, 3), "Lower", lower)) }
        val templates = listOf(st(1, "Upper", upper), st(2, "Lower", lower), st(3, "Arms", ids("a", 4)))
        assertEquals(listOf(1L, null, null, 2L, null, null, null), suggestedWeek(ws, templates, CURRENT_SPLIT_NAME, sToday, same))
    }

    @Test
    fun `half the active weeks is enough and fewer is rest`() {
        val templates = listOf(st(1, "Upper", upper), st(2, "Lower", lower))
        fun week(sat: Int) = (1..8).flatMap { w ->
            listOf(workout(weekDay(w, 0), "Upper", upper)) + if (w <= sat) listOf(workout(weekDay(w, 5), "Lower", lower)) else emptyList()
        }
        assertEquals(2L, suggestedWeek(week(4), templates, CURRENT_SPLIT_NAME, sToday, same)[5])
        assertNull(suggestedWeek(week(3), templates, CURRENT_SPLIT_NAME, sToday, same)[5])
        // A week with no workout at all does not count against a day.
        val gap = (1..8).filter { it != 2 && it != 3 }.map { workout(weekDay(it, 5), "Lower", lower) } + workout(weekDay(1, 0), "Upper", upper)
        assertEquals(2L, suggestedWeek(gap, templates, CURRENT_SPLIT_NAME, sToday, same)[5])
    }

    @Test
    fun `the most common name wins and a tie goes to the most recent`() {
        val templates = listOf(st(1, "Upper", upper), st(2, "Lower", lower))
        val names = listOf("Lower", "Upper", "Upper", "Lower", "Upper", "Upper", "Lower", "Lower") // week 1 first
        val ws = names.mapIndexed { i, n -> workout(weekDay(i + 1, 0), n, if (n == "Upper") upper else lower) }
        // Four of each; the most recent (week 1) is Lower.
        assertEquals(2L, suggestedWeek(ws, templates, CURRENT_SPLIT_NAME, sToday, same)[0])
        val more = ws + workout(weekDay(8, 0), "Upper", upper, "11:00:00")
        assertEquals(1L, suggestedWeek(more, templates, CURRENT_SPLIT_NAME, sToday, same)[0])
    }

    @Test
    fun `a name without a template falls to the best overlap or to rest`() {
        val ws = (1..8).map { workout(weekDay(it, 1), "Gone", upper) }
        val close = st(1, "Close", upper.take(7) + "z")
        val far = st(2, "Far", lower)
        assertEquals(1L, suggestedWeek(ws, listOf(far, close), CURRENT_SPLIT_NAME, sToday, same)[1])
        assertNull(suggestedWeek(ws, listOf(far), CURRENT_SPLIT_NAME, sToday, same)[1])
    }

    @Test
    fun `too little history falls back to the current split on spread days`() {
        val templates = (1L..7L).map { st(it, "T$it", listOf("x$it"), CURRENT_SPLIT_NAME) } + st(9, "Other", listOf("o"))
        val got = suggestedWeek(emptyList(), templates, CURRENT_SPLIT_NAME, sToday, same)
        assertEquals(listOf(1L, 4L, 2L, 5L, 3L, 6L, null), got)
        assertEquals(listOf(1L, null, 2L, null, null, null, null), suggestedWeek(emptyList(), templates.take(2), CURRENT_SPLIT_NAME, sToday, same))
        assertEquals(List(7) { null }, suggestedWeek(emptyList(), listOf(st(9, "Other", listOf("o"))), CURRENT_SPLIT_NAME, sToday, same))
        // Workouts older than eight weeks or in this week are no history.
        val old = listOf(workout(weekDay(9, 1), "T7", listOf("x7")), workout(sMonday.plusDays(1), "T7", listOf("x7")))
        assertEquals(listOf(7L, null, null, null, null, null, null), suggestedWeek(old, listOf(st(7, "T7", listOf("x7"), CURRENT_SPLIT_NAME)), CURRENT_SPLIT_NAME, sToday, same))
    }

    @Test
    fun `an overall name is what the user stored, else the library's, else itself`() {
        assertEquals("custom group", overallName("My lift") { "custom group" })
        assertEquals("My lift", overallName("My lift") { NO_GROUP })
        assertEquals("My lift", overallName("My lift") { null })
        assertEquals("My lift", overallName("My lift") { "" })
    }

    private companion object {
        const val CURRENT_SPLIT_NAME = "Current split"
    }
}
