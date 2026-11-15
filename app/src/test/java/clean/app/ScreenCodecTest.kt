package clean.app

import clean.core.Head
import clean.core.Muscle
import clean.core.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every page reads back as it was written, so the back stack survives the process being killed. */
class ScreenCodecTest {
    private val pages = listOf(
        Screen.Active, Screen.Settings, Screen.ExerciseSetup, Screen.Chart, Screen.Trends, Screen.Bmi, Screen.Trophies, Screen.Program,
        Screen.PersonalBests, Screen.History, Screen.Body, Screen.Measurements, Screen.EditHome, Screen.EditProfile, Screen.Navigation,
        Screen.Shortcuts, Screen.Photos,
        Screen.TemplateEdit(null), Screen.TemplateEdit(12), Screen.WorkoutDetail("2026-10-01 18:00:00"),
        Screen.ExerciseDetail("Bench Press (Barbell)"), Screen.ExerciseDetail("Squat  ", edit = true, tab = 2),
        Screen.Summary("2026-10-01 18:00:00"), Screen.Week("2026-09-28"), Screen.MeasurementDetail("weight.csv"),
        Screen.MuscleDetail(Muscle.entries.first()), Screen.HeadDetail(Head.entries.last()), Screen.Routine("Push, A"),
        Screen.TemplateStats(3), Screen.Month("2026-09"), Screen.PrTimeline("Deadlift"), Screen.Calendar(), Screen.Calendar("2026-09-03"),
        Screen.Overall("Row"), Screen.NoteReview(), Screen.NoteReview("Row"), Screen.Photo("20261001-080000_front.jpg", Pose.entries.first()),
        Screen.Photo("x.jpg", null), Screen.PhotoCompare(null), Screen.PhotoCompare(Pose.entries.last()),
    )

    @Test
    fun `every page round-trips`() {
        for (p in pages) assertEquals(p, decodeScreen(encodeScreen(p)))
    }

    @Test
    fun `an unknown page is left out`() {
        assertNull(decodeScreen("NoSuchPage"))
        assertNull(decodeScreen("MuscleDetail\u001FNOT_A_MUSCLE"))
    }
}
