package clean.app

import clean.app.android.*

import clean.core.time.*
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.open
import clean.app.data.ExerciseSetting
import clean.app.data.Kv
import clean.app.data.MeasurementEntity
import clean.app.data.Profile
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.TemplateEntity
import clean.core.STRONG_DATE_FORMAT
import clean.core.SetType
import clean.core.WorkoutRow
import clean.core.decimalText
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.datetime.LocalDate

/**
 * Round 11b: where the UI spends its time (Robolectric, software rendering: the ranking is meaningful, the absolute numbers are not a phone's).
 * Run only with CLEAN_SPEED=1. One pass (a fresh launch over the made-up log) per Gradle run; run it several times and aggregate build/speed-audit-raw.txt.
 * The clock is paused and stepped one frame at a time; a frame is timed as "advance" (recomposition + measure + layout, which the test
 * rule runs when it idles) plus "draw" (the decor view drawn to a software bitmap). Haze cannot blur here (glass.reduce is on), so the
 * blurs are not in these numbers; they are estimated from the code in the log.
 * Output: build/speed-audit.txt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-mdpi")
class SpeedAuditTest {
    @get:Rule val compose = createEmptyComposeRule()

    class F(val adv: Double, val draw: Double) { val total get() = adv + draw }

    companion object {
        var passes = 0
        /** Scalar results (ms) per metric, one per measured pass. */
        val scalars = LinkedHashMap<String, MutableList<Double>>()
        /** Frames pooled over measured passes, per metric. */
        val frames = LinkedHashMap<String, MutableList<F>>()
        val notes = ArrayList<String>()
    }

    private val names = listOf(
        "Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)", "Overhead Press (Barbell)", "Bicep Curl (Dumbbell)",
        "Triceps Pushdown (Cable)", "Romanian Deadlift (Barbell)", "Lateral Raise (Dumbbell)",
    )

    private fun seed() = runBlocking {
        val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
        val id = repo.createProfile("Rohan")
        repo.dao.put(Kv("active", id.toString()))
        repo.dao.put(Kv("welcome:$id", "done"))
        repo.dao.put(Kv("weekpill:$id", clean.core.weekStart(LocalDate.now()).toString()))
        repo.dao.put(Kv("glass.reduce", "on"))
        repo.dao.put(Kv(clean.core.LOOK_THEME_KEY, "dark"))
        repo.dao.put(Kv(clean.core.LOOK_BLACK_KEY, "on"))
        repo.dao.put(Kv(clean.core.LOOK_DONE_KEY, "1"))
        repo.dao.put(Kv(clean.core.LOOK_ACCENT_KEY, "Cobalt"))
        repo.dao.put(Kv(clean.core.LOOK_V2_KEY, "1"))
        repo.dao.put(Kv("ui.motion", "on"))
        val rows = ArrayList<WorkoutRow>()
        val first = LocalDate.now().minusWeeks(30)
        var n = 0
        for (week in 0 until 30) for (offset in listOf(0L, 2L, 4L)) {
            n++
            val day = first.plusWeeks(week.toLong()).plusDays(offset)
            if (day >= LocalDate.now()) continue
            val date = day.atTime(18, 0).format(STRONG_DATE_FORMAT)
            val name = listOf("Push", "Pull", "Legs")[n % 3]
            for (e in 0 until 4) {
                val exercise = names[(n + e * 3) % names.size]
                rows += WorkoutRow(n.toString(), date, name, "3900", exercise, "W", "20.0", "10", "", "", "", "", "")
                for (s in 1..3) rows += WorkoutRow(n.toString(), date, name, "3900", exercise, s.toString(), decimalText(40.0 + n * 0.5 + e * 5), (8 + s % 3).toString(), "", "", "", "", "")
            }
        }
        repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
        for (t in listOf("Push", "Pull", "Legs")) {
            val blocks = names.take(6).joinToString(",") { "{\"exercise\":\"$it\",\"sets\":[\"\",\"\",\"\"]}" }
            repo.dao.put(TemplateEntity(0, id, t, "[$blocks]"))
        }
        repo.dao.update(Profile(id, "Rohan", "kg", 178.0))
        repo.dao.put(ExerciseSetting(id, "Bench Press (Barbell)", goal = "100.0x5"))
        repo.dao.put(Kv("goal.weight:$id", "78.0|82.0|${LocalDate.now().minusWeeks(20)}"))
        repo.dao.insertMeasurements((0 until 4).flatMap { k ->
            val at = LocalDate.now().minusWeeks(k * 3L).atTime(8, 0).format(STRONG_DATE_FORMAT)
            listOf(MeasurementEntity(0, id, "waist.csv", at, decimalText(84.0 - k)), MeasurementEntity(0, id, "chest.csv", at, decimalText(101.0 + k * 0.5)))
        })
        repo.dao.insertMeasurements((0 until 30).map { MeasurementEntity(0, id, "weight.csv", LocalDate.now().minusWeeks(it.toLong()).atTime(8, 0).format(STRONG_DATE_FORMAT), decimalText(82.0 - it * 0.1)) })
    }

    private lateinit var model: AppModel
    private var window: android.view.View? = null
    private var bitmap: Bitmap? = null
    private var measured = false

    private fun ms(from: Long) = (System.nanoTime() - from) / 1e6

    private fun scalar(key: String, v: Double) { if (measured) scalars.getOrPut(key) { ArrayList() } += v }
    private fun frames(key: String, fs: List<F>) { if (measured) frames.getOrPut(key) { ArrayList() } += fs }

    /** One 16 ms frame: the clock stepped and the screen idled (recompose, measure, layout), then the window drawn. */
    private fun step(before: () -> Unit = {}): F {
        val t0 = System.nanoTime()
        before()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val adv = ms(t0)
        val t1 = System.nanoTime()
        window!!.draw(android.graphics.Canvas(bitmap!!))
        return F(adv, ms(t1))
    }

    private fun settle(ms: Long = 2500) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    private fun root() = compose.onAllNodes(isRoot()).onFirst()

    /** A finger drag, a frame per step (the step's input is in the frame's time), then the release and [settle] frames of the fling or glide. */
    private fun drag(from: Offset, d: Offset, steps: Int, settle: Int, labelAt: ((Int) -> String)? = null): List<F> {
        val out = ArrayList<F>()
        out += step { root().performTouchInput { down(from) } }
        repeat(steps) { out += step { root().performTouchInput { moveBy(d, 16) } } }
        out += step { root().performTouchInput { up() } }
        repeat(settle) { out += step() }
        return out
    }

    private fun launch() {
        seed()
        compose.mainClock.autoAdvance = false
        val t0 = System.nanoTime()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        val deadline = System.currentTimeMillis() + 90_000
        var n = 0
        while (System.currentTimeMillis() < deadline && !(model.loaded && !model.activityY.getValue(Tab.Home).floatValue.isNaN())) {
            compose.mainClock.advanceTimeByFrame(); Thread.sleep(8); n++
        }
        check(model.loaded) { "did not load" }
        val view = window!!
        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        scalar("a: Home, launch to first settled-in frame (incl. data load, cold JIT)", ms(t0))
    }

    private fun pass() {
        Assume.assumeTrue(System.getenv("CLEAN_SPEED") == "1")
        passes++
        measured = true
        launch()
        val cold = model.activityY.getValue(Tab.Progress).floatValue.isNaN()
        if (true) notes += "Progress composed before its first visit: ${!cold}"
        compose.mainClock.advanceTimeBy(100)

        // (a) first visit of each tab, 30 frames from the tap (the blink is part of it), then the same tab again when warm.
        fun visit(label: String, tab: Tab) {
            val fs = ArrayList<F>()
            fs += step { compose.runOnIdle { model.tab = tab } }
            repeat(29) { fs += step() }
            scalar("a: $label tab, first visit, worst frame", fs.maxOf { it.total })
            scalar("a: $label tab, first visit, sum of 30 frames", fs.sumOf { it.total })
            frames("a: $label tab first visit", fs)
            settle(1500)
        }
        for ((l, t) in listOf("Progress" to Tab.Progress, "Library" to Tab.Exercises, "Profile" to Tab.Profile)) visit(l, t)
        // A warm switch for comparison.
        run {
            val fs = ArrayList<F>()
            fs += step { compose.runOnIdle { model.tab = Tab.Home } }
            repeat(29) { fs += step() }
            scalar("a: warm switch to Home, worst frame (baseline)", fs.maxOf { it.total })
            scalar("a: warm switch to Home, sum of 30 frames (baseline)", fs.sumOf { it.total })
            settle(1500)
        }
        // All four warm now.
        settle(3000)

        // (c) the pager swiped across all four tabs, there and back, quickly (10 steps of 40 px at 16 ms, then the glide).
        val bounds = listOf("Home>Progress", "Progress>Library", "Library>Profile")
        val allSwipes = ArrayList<F>()
        for ((i, b) in bounds.withIndex()) {
            val fs = drag(Offset(360f, 100f), Offset(-40f, 0f), 10, 25)
            frames("c: swipe $b", fs); allSwipes += fs
            settle(800)
        }
        for ((i, b) in bounds.reversed().withIndex()) {
            val fs = drag(Offset(40f, 100f), Offset(40f, 0f), 10, 25)
            frames("c: swipe back ${b.split(">").reversed().joinToString(">")}", fs); allSwipes += fs
            settle(800)
        }
        frames("c: pager swipe, all six boundaries", allSwipes)
        compose.runOnIdle { model.tab = Tab.Home }; settle(2500)

        // (d) scrolling: Home, Progress, History (fast flicks: 12 steps of 60 px, then the fling).
        fun scroll(label: String) {
            val down = drag(Offset(200f, 700f), Offset(0f, -60f), 12, 30)
            val up = drag(Offset(200f, 200f), Offset(0f, 60f), 12, 30)
            frames("d: scroll $label", down + up)
            settle(800)
        }
        scroll("Home")
        compose.runOnIdle { model.tab = Tab.Progress }; settle(2500)
        scroll("Progress")
        compose.runOnIdle { model.tab = Tab.Home }; settle(2500)
        compose.runOnIdle { model.stack.clear(); model.openHistory() }; settle(2500)
        scroll("History")
        compose.runOnIdle { model.stack.clear(); model.tab = Tab.Home }; settle(2500)

        // (e) the Home weight card growing into the weight page, frame by frame.
        run {
            // scroll the card into view if it is below the fold
            repeat(4) {
                val b = compose.onAllNodes(androidx.compose.ui.test.hasTestTag("card:home:weight"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
                if (b != null && b.top > 0f && b.bottom < 700f) return@repeat
                drag(Offset(200f, 700f), Offset(0f, -60f), 6, 25); settle(500)
            }
            val node = compose.onAllNodes(androidx.compose.ui.test.hasTestTag("card:home:weight"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            if (node == null) { if (true) notes += "e: card:home:weight not found, morph not measured" }
            else {
                val fs = ArrayList<F>()
                fs += step { compose.runOnIdle { model.fromCard("home:weight") { model.open(Screen.MeasurementDetail("weight.csv")) } } }
                repeat(40) { fs += step() }
                check(model.stack.size >= 1) { "the weight card did not open a page (tap replaced by the card press path, model.fromCard)" }
                frames("e: Home weight card to weight page (40 frames)", fs)
                scalar("e: morph card to page, sum of 40 frames", fs.sumOf { it.total })
            }
            settle(1500)
            compose.runOnIdle { model.stack.clear(); model.tab = Tab.Home }; settle(2000)
        }

        // (b) opening each page: the first frame (the open call, recomposition, layout, draw) and the 20 frames after it, clock-stepped (the animation's duration is not counted, only the work).
        fun open(label: String, screen: () -> Unit) {
            compose.runOnIdle { model.stack.clear() }; settle(2000)
            val fs = ArrayList<F>()
            fs += step { compose.runOnIdle(screen) }
            repeat(19) { fs += step() }
            scalar("b: open $label, first frame", fs.first().total)
            scalar("b: open $label, worst frame of first 20", fs.maxOf { it.total })
            scalar("b: open $label, sum of first 20 frames", fs.sumOf { it.total })
            frames("b: open $label", fs)
            settle(1500)
        }
        compose.runOnIdle { model.logWeightNow = false }
        open("weight page") { model.open(Screen.MeasurementDetail("weight.csv")) }
        open("exercise page") { model.open(Screen.ExerciseDetail(names[0])) }
        open("Calendar") { model.open(Screen.Calendar()) }
        open("History") { model.open(Screen.History) }
        open("Moments") { model.open(Screen.Moments) }
        open("Year in review") { model.open(Screen.YearReview(LocalDate.now().year)) }
        open("Settings") { model.open(Screen.Settings) }
        open("Body") { model.open(Screen.Body) }
        open("running workout") {
            model.putDraft(Draft("Push", System.currentTimeMillis() - 20 * 60_000, names.take(3).map { DraftBlock(it, listOf(DraftSet(SetType.WARMUP), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))) }))
            model.open(Screen.Active)
        }
        writeReport()
        val raw = StringBuilder("RUN\n")
        for ((k, v) in scalars) for (x in v) raw.append("S\t$k\t$x\n")
        for ((k, v) in frames) for (f in v) raw.append("F\t$k\t${f.adv}\t${f.draw}\n")
        File("build/speed-audit-raw.txt").appendText(raw.toString())
    }

    private fun pct(v: List<Double>, p: Double) = v.sorted().let { it[((it.size - 1) * p).toInt().coerceIn(0, it.size - 1)] }
    private fun med(v: List<Double>) = pct(v, 0.5)

    private fun writeReport() {
        val sb = StringBuilder()
        sb.appendLine("passes run: $passes (first is warm-up); notes: $notes")
        sb.appendLine("== scalars: metric | median over passes | worst | n")
        for ((k, v) in scalars) sb.appendLine("%-90s | %8.1f | %8.1f | %d".format(k, med(v), v.max(), v.size))
        sb.appendLine("== frames (pooled over passes): metric | n | median total | p90 | worst | median adv | median draw")
        for ((k, v) in frames) {
            val t = v.map { it.total }
            sb.appendLine("%-70s | %4d | %7.1f | %7.1f | %7.1f | %7.1f | %7.1f".format(k, v.size, med(t), pct(t, 0.9), t.max(), med(v.map { it.adv }), med(v.map { it.draw })))
        }
        sb.appendLine("== worst frame index within each frame set (0 = the frame carrying the input / open call)")
        File("build").mkdirs()
        File("build/speed-audit.txt").writeText(sb.toString())
        println(sb)
    }

    // One pass per Gradle run (a second launch in the same JVM does not load reliably); raw results are appended to build/speed-audit-raw.txt.
    @Test fun pass1() = pass()
}
