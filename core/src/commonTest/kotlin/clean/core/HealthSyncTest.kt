package clean.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Health Connect's decisions, which need no phone: the notes summary, trimming, the plan, the ledger, chunks, retries and the words. */
private class SecurityException(message: String) : Exception(message)

class HealthSyncTest {
    private fun row(date: String, exercise: String, order: String, weight: String, reps: String, name: String = "Push", duration: String = "3600", workoutNotes: String = "", seconds: String = "", distance: String = "") =
        WorkoutRow("1", date, name, duration, exercise, order, weight, reps, "", distance, seconds, "", workoutNotes)

    private fun workout(vararg rows: WorkoutRow) = buildWorkouts(rows.toList()).single()

    /** Runs a suspend function to its end on the spot (the fake pause never suspends). */
    private fun <T> run(block: suspend () -> T): T {
        var out: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
        return out!!.getOrThrow()
    }

    // ---- the notes summary

    @Test fun theSummaryIsTheExampleInTheBrief() {
        val d = "2026-11-20 18:00:00"
        val w = workout(
            row(d, "Bench Press (Barbell)", "1", "60.0", "8"), row(d, "Bench Press (Barbell)", "2", "60.0", "8"), row(d, "Bench Press (Barbell)", "3", "60.0", "8"),
            row(d, "Squat (Barbell)", "1", "100.0", "5"), row(d, "Squat (Barbell)", "2", "100.0", "5"), row(d, "Squat (Barbell)", "3", "100.0", "5"),
            row(d, "Squat (Barbell)", "4", "100.0", "5"), row(d, "Squat (Barbell)", "5", "100.0", "5"),
        )
        assertEquals("Bench Press (Barbell) 3 × 8 @ 60 kg · Squat (Barbell) 5 × 5 @ 100 kg", workoutSummary(w, "kg"))
    }

    @Test fun warmUpsAreLeftOutAndTheTopWeightIsShownWhenSetsDiffer() {
        val d = "2026-11-20 18:00:00"
        val w = workout(
            row(d, "Bench", "W", "20.0", "10"), row(d, "Bench", "1", "60.0", "8"), row(d, "Bench", "2", "62.5", "5"), row(d, "Bench", "3", "62.5", "6"), row(d, "Row", "W", "20.0", "10"),
        )
        // The row exercise has only a warm-up, so it is left out.
        assertEquals("Bench 3 sets, top 62.5 kg × 6", workoutSummary(w, "kg"))
    }

    @Test fun theProfilesUnitIsUsed() {
        val d = "2026-11-20 18:00:00"
        val w = workout(row(d, "Bench", "1", "60.0", "8"), row(d, "Bench", "2", "60.0", "8"))
        assertEquals("Bench 2 × 8 @ 132.5 lb", workoutSummary(w, "lb"))
    }

    @Test fun theWorkoutNoteGoesFirstAndBodyweightHasNoWeight() {
        val d = "2026-11-20 18:00:00"
        val w = workout(row(d, "Pull Up", "1", "0.0", "10", workoutNotes = "Felt strong"), row(d, "Pull Up", "2", "0.0", "10"))
        assertEquals("Felt strong\nPull Up 2 × 10", workoutSummary(w, "kg"))
    }

    @Test fun timedAndDistanceSetsAreNotGivenRepsTheyDidNotHave() {
        val d = "2026-11-20 18:00:00"
        val w = workout(row(d, "Plank", "1", "0.0", "", seconds = "45"), row(d, "Run", "1", "0.0", "", distance = "5000"))
        assertEquals("Plank 1 × 45 s · Run 1 × 5 km", workoutSummary(w, "kg"))
    }

    @Test fun aLongSummaryIsCutAndSaysHowManyWereLeftOut() {
        val d = "2026-11-20 18:00:00"
        val rows = (1..80).map { row(d, "Exercise number $it with a long name", "1", "60.0", "8") }
        val text = workoutSummary(workout(*rows.toTypedArray()), "kg")
        assertTrue(text.length <= HEALTH_NOTES_MAX, "${text.length}")
        assertTrue(Regex("· \\+\\d+ more$").containsMatchIn(text), text)
    }

    @Test fun aWorkoutWithNoSetsHasNoSummaryButItsNote() {
        val d = "2026-11-20 18:00:00"
        val w = workout(WorkoutRow("1", d, "Push", "3600", "Bench", "Note", "", "", "", "", "", "", "a note"))
        assertEquals("a note", workoutSummary(w, "kg"))
    }

    // ---- the title

    @Test fun aBlankNameIsSentAsWorkoutAndALongOneIsCut() {
        assertEquals("Workout", healthTitle(""))
        assertEquals("Workout", healthTitle("   "))
        assertEquals("Push", healthTitle("Push"))
        assertEquals(HEALTH_TITLE_MAX, healthTitle("x".repeat(300)).length)
    }

    // ---- overlaps

    @Test fun aWorkoutThatRunsIntoTheNextEndsASecondBeforeIt() {
        // 10:00 for 3 hours, then a workout at 11:00, then one at 18:00 for an hour.
        val t = 36_000L
        assertEquals(listOf(3599L, 3600L, 3600L), trimmedSeconds(listOf(t, t + 3600, t + 8 * 3600), listOf(10_800, 3600, 3600)))
    }

    @Test fun trimmingDoesNotDependOnTheOrderGiven() {
        val t = 36_000L
        assertEquals(listOf(3600L, 3599L), trimmedSeconds(listOf(t + 3600, t), listOf(3600, 7200)))
    }

    @Test fun aWorkoutWithNoLengthIsAMinuteAndNeverLessThanASecond() {
        assertEquals(listOf(60L), trimmedSeconds(listOf(0L), listOf(0L)))
        assertEquals(listOf(1L, 3600L), trimmedSeconds(listOf(0L, 1L), listOf(3600, 3600)))
    }

    @Test fun anExactTouchIsTrimmedToo() {
        assertEquals(listOf(3599L, 60L), trimmedSeconds(listOf(0L, 3600L), listOf(3600, 60)))
    }

    // ---- what should be there

    @Test fun theDesiredRecordsAreWorkoutsWeightAndBodyFatWithTheOldIds() {
        val d = "2026-11-20 18:00:00"
        val w = workout(row(d, "Bench", "1", "60.0", "8"))
        val got = desiredHealth(7, "kg", listOf(w), listOf(
            HealthEntry("weight.csv", "2026-11-20 08:00:00", "82.4"), HealthEntry("body_fat_percentage.csv", "2026-11-20 08:00:00", "18.5"),
            HealthEntry("waist.csv", "2026-11-20 08:00:00", "84"), HealthEntry("weight.csv", "2026-11-19 08:00:00", "not a number"),
            HealthEntry("body_fat_percentage.csv", "2026-11-18 08:00:00", "180"),
        ))
        assertEquals(
            listOf("clean:workout:7:2026-11-20 18:00:00", "clean:weight:7:2026-11-20 08:00:00", "clean:bodyfat:7:2026-11-20 08:00:00"),
            got.map { it.id },
        )
        assertEquals("Push", (got[0] as HealthWorkout).title)
        assertEquals(18.5, (got[2] as HealthBodyFat).percent)
    }

    @Test fun aBlankNameGivesTheTitleWorkout() {
        val w = workout(row("2026-11-20 18:00:00", "Bench", "1", "60.0", "8", name = ""))
        assertEquals("Workout", (desiredHealth(1, "kg", listOf(w), emptyList()).single() as HealthWorkout).title)
    }

    @Test fun overlappingWorkoutsAreTrimmedInTheDesiredList() {
        val a = workout(row("2026-11-20 10:00:00", "Bench", "1", "60.0", "8", duration = "10800"))
        val b = workout(row("2026-11-20 11:00:00", "Bench", "1", "60.0", "8", duration = "3600"))
        val got = desiredHealth(1, "kg", listOf(a, b), emptyList()).map { (it as HealthWorkout).seconds }
        assertEquals(listOf(3599L, 3600L), got)
    }

    // ---- the plan

    private fun weight(date: String, kg: Double, profile: Long = 1) = HealthWeight(healthId(HealthKind.WEIGHT, profile, date), date, kg)

    @Test fun onlyWhatIsMissingOrChangedIsWrittenAndWhatIsGoneIsRemoved() {
        val a = weight("2026-11-01 08:00:00", 80.0)
        val b = weight("2026-11-02 08:00:00", 81.0)
        val c = weight("2026-11-03 08:00:00", 82.0)
        val ledger = mapOf(a.id to a.fingerprint, b.id to weight("2026-11-02 08:00:00", 80.5).fingerprint, "clean:weight:1:2026-10-01 08:00:00" to "x")
        val plan = planHealth(listOf(a, b, c), ledger, 1, setOf(1))
        assertEquals(listOf(b.id, c.id), plan.write.map { it.id })
        assertEquals(listOf("clean:weight:1:2026-10-01 08:00:00"), plan.remove)
    }

    @Test fun everythingIsWrittenAgainWhenForced() {
        val a = weight("2026-11-01 08:00:00", 80.0)
        assertEquals(listOf(a.id), planHealth(listOf(a), mapOf(a.id to a.fingerprint), 1, setOf(1), force = true).write.map { it.id })
        assertTrue(planHealth(listOf(a), mapOf(a.id to a.fingerprint), 1, setOf(1)).isEmpty)
    }

    @Test fun anotherProfilesRecordsStayUnlessItsProfileIsGone() {
        val other = weight("2026-11-01 08:00:00", 70.0, profile = 2)
        val gone = weight("2026-11-01 08:00:00", 60.0, profile = 3)
        val plan = planHealth(emptyList(), mapOf(other.id to "x", gone.id to "y"), active = 1, profiles = setOf(1, 2))
        assertEquals(listOf(gone.id), plan.remove)
    }

    @Test fun deletingEverythingRemovesEverythingOfTheProfile() {
        val a = weight("2026-11-01 08:00:00", 80.0)
        assertEquals(listOf(a.id), planHealth(emptyList(), mapOf(a.id to a.fingerprint), 1, setOf(1)).remove)
    }

    @Test fun aSummaryOrTitleChangeChangesTheFingerprint() {
        val a = HealthWorkout("clean:workout:1:d", "d", "Push", "x", 60)
        assertNotEquals(a.fingerprint, a.copy(notes = "y").fingerprint)
        assertNotEquals(a.fingerprint, a.copy(title = "Pull").fingerprint)
        assertNotEquals(a.fingerprint, a.copy(seconds = 61).fingerprint)
        assertEquals(a.fingerprint, a.copy().fingerprint)
    }

    @Test fun theLedgerSurvivesBeingWrittenAsText() {
        val m = mapOf("clean:weight:1:2026-11-01 08:00:00" to "abc", "clean:workout:1:2026-11-01 18:00:00" to "def")
        assertEquals(m, decodeLedger(encodeLedger(m)))
        assertEquals(emptyMap(), decodeLedger(""))
        assertEquals(emptyMap(), decodeLedger("junk\n\t\nid\t"))
    }

    @Test fun idsSayWhatTheyAreAndWhoseTheyAre() {
        assertEquals(HealthKind.BODY_FAT, healthKindOf("clean:bodyfat:4:2026-11-01 08:00:00"))
        assertEquals(4L, healthProfileOf("clean:bodyfat:4:2026-11-01 08:00:00"))
        assertNull(healthProfileOf("other:1:x"))
    }

    // ---- chunks, retries and failures

    @Test fun recordsGoInChunksOfAtMostAThousand() {
        val sizes = ArrayList<Int>()
        val out = run { runHealthChunks((1..2500).toList(), pause = {}) { sizes += it.size } }
        assertEquals(listOf(1000, 1000, 500), sizes)
        assertEquals(2500, out.done.size)
        assertNull(out.failure)
    }

    @Test fun aFailedChunkIsRetriedAloneAfterTheBackoffAndTheOthersAreNotSentTwice() {
        val calls = ArrayList<List<Int>>()
        val waits = ArrayList<Long>()
        var failures = 2
        val out = run {
            runHealthChunks((1..25).toList(), size = 10, backoff = listOf(5L, 50L, 500L), pause = { waits += it }) { chunk ->
                calls += chunk
                if (chunk.first() == 11 && failures-- > 0) throw IllegalStateException("API call quota exceeded")
            }
        }
        assertEquals(listOf(1, 11, 11, 11, 21), calls.map { it.first() })
        assertEquals(listOf(5L, 50L), waits)
        assertEquals(25, out.done.size)
        assertNull(out.failure)
    }

    @Test fun aRateLimitThatNeverEasesStopsTheRunAndSaysSo() {
        val calls = ArrayList<Int>()
        val out = run {
            runHealthChunks((1..30).toList(), size = 10, backoff = listOf(1L, 2L), pause = {}) { chunk ->
                calls += chunk.first()
                if (chunk.first() == 11) throw RuntimeException("Rate limit exceeded")
            }
        }
        assertEquals(listOf(1, 11, 11, 11), calls)
        assertEquals(10, out.done.size)
        assertEquals(20, out.failed)
        assertEquals(HealthFailureKind.RATE_LIMIT, out.failure?.kind)
    }

    @Test fun aMissingPermissionIsNotRetriedAndStopsTheRun() {
        var calls = 0
        val out = run {
            runHealthChunks((1..30).toList(), size = 10, pause = { error("no waiting for a permission") }) { calls++; throw SecurityException("Caller doesn't have permission") }
        }
        assertEquals(1, calls)
        assertEquals(30, out.failed)
        assertEquals(HealthFailureKind.PERMISSION, out.failure?.kind)
    }

    @Test fun anotherFaultLeavesThatChunkAndGoesOn() {
        val out = run {
            runHealthChunks((1..30).toList(), size = 10, backoff = emptyList(), pause = {}) { if (it.first() == 11) throw IllegalStateException("disk full") }
        }
        assertEquals(20, out.done.size)
        assertEquals(10, out.failed)
        assertEquals(HealthFailureKind.OTHER, out.failure?.kind)
    }

    @Test fun cancellationIsNeverSwallowedOrRetried() {
        var calls = 0
        assertFailsWith<CancellationException> {
            run { runHealthChunks((1..30).toList(), size = 10, pause = { error("not retried") }) { calls++; throw CancellationException("stop") } }
        }
        assertEquals(1, calls)
    }

    @Test fun failuresAreSortedIntoThreeKinds() {
        assertEquals(HealthFailureKind.PERMISSION, classifyHealthFailure("java.lang.SecurityException", "x").kind)
        assertEquals(HealthFailureKind.PERMISSION, classifyHealthFailure("X", "Permission denied").kind)
        assertEquals(HealthFailureKind.RATE_LIMIT, classifyHealthFailure("HealthConnectException", "ERROR_RATE_LIMIT_EXCEEDED").kind)
        assertEquals(HealthFailureKind.RATE_LIMIT, classifyHealthFailure("X", "API call quota exceeded").kind)
        assertEquals(HealthFailureKind.OTHER, classifyHealthFailure("java.io.IOException", "broken pipe").kind)
    }

    // ---- the words

    @Test fun theLastResultIsHonest() {
        assertEquals("Sent 3, removed 1", healthReportText(HealthReport(written = listOf("a", "b", "c"), removed = listOf("d"))))
        assertEquals("Sent 3", healthReportText(HealthReport(written = listOf("a", "b", "c"))))
        assertEquals("Up to date", healthReportText(HealthReport()))
        assertEquals("Health Connect refused: permission removed", healthReportText(HealthReport(failed = 4, failure = HealthFailure(HealthFailureKind.PERMISSION))))
        assertEquals("Sent 2; 5 not sent: rate limited", healthReportText(HealthReport(written = listOf("a", "b"), failed = 5, failure = HealthFailure(HealthFailureKind.RATE_LIMIT))))
        assertEquals("Health Connect failed: not available on this phone", healthReportText(HealthReport(failure = HealthFailure(HealthFailureKind.OTHER, "not available on this phone"))))
        assertEquals("Sent 1 (not allowed: body fat)", healthReportText(HealthReport(written = listOf("a"), notAllowed = setOf(HealthKind.BODY_FAT))))
    }
}
