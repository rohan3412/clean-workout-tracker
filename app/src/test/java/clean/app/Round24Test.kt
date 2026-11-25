package clean.app

import org.junit.experimental.categories.Category
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.ExerciseSetting
import clean.app.data.Kv
import clean.app.data.MeasurementEntity
import clean.app.data.Profile
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.TemplateEntity
import clean.app.data.open
import clean.app.setWeightGoal
import clean.core.ChartPoint
import clean.core.PrKind
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.decimalText
import clean.core.time.*
import java.io.File
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 24: the nooks' second half and the card's line on Back.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round24Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: android.view.View? = null
    private lateinit var model: AppModel
    private var profileId = 0L
    private var launched: ActivityScenario<MainActivity>? = null

    private fun seed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = Repo(CleanDb.open(context))
        val id = repo.createProfile("Test")
        repo.dao.put(Kv("active", id.toString()))
        repo.dao.put(Kv("welcome:$id", "done"))
        repo.dao.put(Kv("weekpill:$id", clean.core.weekStart(LocalDate.now()).toString()))
        repo.dao.put(Kv("glass.reduce", "on"))
        val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val start = java.time.LocalDateTime.now().minusDays(40).withHour(18).withMinute(0).withSecond(0)
        val rows = (0 until 14).flatMap { n ->
            val date = start.plusDays(n * 3L).format(format)
            val name = if (n % 2 == 0) "Push" else "Pull"
            val lift = if (n % 2 == 0) "Bench Press (Barbell)" else "Lat Pulldown (Machine)"
            (1..3).map { set -> "\"${n + 1}\";\"$date\";\"$name\";\"3600\";\"$lift\";\"$set\";\"${60 + n + (n * 7 % 5)}.0\";\"${8 + n % 3}\";\"\";\"\";\"\";\"\";\"\"" }
        }
        repo.commit(id, repo.previewWorkouts(id, (listOf(header) + rows).joinToString("\n", postfix = "\n").toByteArray()))
    }

    private fun launch(): ActivityScenario<MainActivity> {
        seed()
        runBlocking {
            val dao = Repo(CleanDb.open(ApplicationProvider.getApplicationContext<android.app.Application>())).dao
            dao.put(Kv(clean.core.LOOK_THEME_KEY, "dark"))
            dao.put(Kv(clean.core.LOOK_BLACK_KEY, "on"))
            dao.put(Kv(clean.core.LOOK_DONE_KEY, "1"))
            dao.put(Kv(clean.core.LOOK_ACCENT_KEY, "Cobalt"))
            dao.put(Kv(clean.core.LOOK_V2_KEY, "1"))
            dao.put(Kv("ui.motion", "on"))
            dao.put(Kv("glass.reduce", "on"))
            dao.put(Kv("ui.bodyMode", "Balance"))
            dao.put(Kv("home.cards:${dao.get("active")}", "last:w,streak:h,volume:h,week:h"))
        }
        MorphProbe.boxes.clear()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded }
        compose.waitForIdle()
        return scenario
    }

    /** A year of crooked daily weigh-ins, so the weekly line of the card and the page's own line differ a lot. */
    private fun weighIns(days: Int = 150) = runBlocking {
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        for (back in days downTo 1) {
            val kg = 80.0 - (days - back) * 0.03 + Math.sin(back * 1.7) * 0.9 + (back % 5) * 0.25
            assertTrue("weigh-in", model.addMeasurement("weight.csv", Math.round(kg * 10) / 10.0, java.time.LocalDateTime.now().minusDays(back.toLong()).withHour(8).withMinute(0).withSecond(0).format(format)))
        }
    }

    private fun frame(): Bitmap {
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    private fun diff(a: Int, b: Int) =
        Math.abs((a shr 16 and 255) - (b shr 16 and 255)) + Math.abs((a shr 8 and 255) - (b shr 8 and 255)) + Math.abs((a and 255) - (b and 255))

    private fun save(name: String, frames: List<Bitmap>, region: Rect? = null, perRow: Int = 6, scale: Int = 1) {
        val r = region ?: Rect(0f, 0f, frames[0].width.toFloat(), frames[0].height.toFloat())
        val x0 = r.left.toInt().coerceAtLeast(0); val y0 = r.top.toInt().coerceAtLeast(0)
        val w = (r.right.toInt().coerceAtMost(frames[0].width) - x0) / scale
        val h = (r.bottom.toInt().coerceAtMost(frames[0].height) - y0) / scale
        val cols = minOf(perRow, frames.size)
        val rows = (frames.size + cols - 1) / cols
        val strip = java.awt.image.BufferedImage((w + 2) * cols, (h + 2) * rows, java.awt.image.BufferedImage.TYPE_INT_RGB)
        frames.forEachIndexed { i, b ->
            val crop = Bitmap.createBitmap(b, x0, y0, w * scale, h * scale)
            val small = Bitmap.createScaledBitmap(crop, w, h, true)
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            strip.setRGB((i % cols) * (w + 2), (i / cols) * (h + 2), w, h, px, 0, w)
        }
        File("build/screens/default").mkdirs()
        javax.imageio.ImageIO.write(strip, "png", File("build/screens/default/round21-$name.png"))
    }

    private fun scrollTo(key: String) {
        val tag = hasTestTag("card:$key")
        try { compose.waitUntil(4000) { compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } } catch (e: Throwable) { }
        val here = compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        if (here != null && here.top >= 0f && here.bottom <= 700f && here.width > 0f) return
        val lists = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (i in 0 until lists) {
            try { compose.onAllNodes(hasScrollAction())[i].performScrollToKey(key.substringAfter(':')); compose.waitForIdle(); return } catch (e: Throwable) { }
        }
        var swipes = 0
        while (compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().let { n -> n.isEmpty() || n.first().boundsInRoot.let { it.width <= 0f || it.bottom > 700f } } && swipes++ < 4) {
            compose.onNodeWithTag("page:tabs", useUnmergedTree = true).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    private fun cardRect(key: String): Rect = compose.onNodeWithTag("card:$key", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** One change stepped 16 ms at a time: all frames, the container's progress in each, and the frame in which it arrived. */
    private class Run(val frames: List<Bitmap>, val grown: List<Float>, val ended: Int, val matched: Set<String>)

    private fun run(act: () -> Unit, ms: Int = 640, forward: Boolean = true): Run {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val frames = mutableListOf(frame())
        val clip = MorphProbe.clip!!
        val grown = mutableListOf(clip.grown)
        compose.runOnIdle(act)
        var seen = clip.draws
        var ended = -1
        val matched = HashSet<String>()
        var t = 0
        while (t < ms) {
            compose.mainClock.advanceTimeBy(16); frames += frame(); t += 16
            for ((k, st) in MorphProbe.states) if (st.isMatchFound) matched += k
            if (clip.draws != seen) { seen = clip.draws; if (Math.abs(clip.grown - grown.last()) > 0.0001f) ended = frames.size - 1; grown += clip.grown } else grown += grown.last()
        }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        compose.mainClock.autoAdvance = true
        return Run(frames, grown, ended, matched)
    }

    private fun lastDrawFor(qs: List<Float>, draws: List<Int>) = draws.indices.last { it == 0 || draws[it] != draws[it - 1] }

    /** Pixels of [a] that differ from [b] (by more than [tol]) inside [r], and their bounding box. */
    private fun differing(a: Bitmap, b: Bitmap, r: Rect, tol: Int = 24): Pair<Int, String> {
        val pa = pixels(a); val pb = pixels(b); val w = a.width
        var n = 0; var x0 = Int.MAX_VALUE; var x1 = -1; var y0 = Int.MAX_VALUE; var y1 = -1
        for (y in r.top.toInt().coerceAtLeast(0) until r.bottom.toInt().coerceAtMost(a.height)) for (x in r.left.toInt().coerceAtLeast(0) until r.right.toInt().coerceAtMost(w)) {
            if (diff(pa[y * w + x], pb[y * w + x]) > tol) { n++; x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y) }
        }
        return n to (if (n == 0) "none" else "x $x0..$x1 y $y0..$y1")
    }


    /**
     * The line of a card closing: on a page of daily weigh-ins over a year, the card's line, in the frames after the move (see [MorphProbe.chartQ]), must not change.
     */
    @Test fun closingTheWeightCardLeavesItsLineAsItIsWhenTheCardLands() {
        val scenario = launch()
        weighIns(365)
        compose.runOnIdle { model.setOption("ui.measurementRange", "365"); model.tab = Tab.Home }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
        scrollTo("home:weight")
        val card = cardRect("home:weight")
        run({ compose.onNodeWithTag("card:home:weight", useUnmergedTree = true).performTouchInput { click(Offset(8f, 8f)) } })
        assertTrue("the card did not open a page", model.stack.isNotEmpty())
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val frames = mutableListOf(frame())
        val qs = mutableListOf(MorphProbe.chartQ)
        val draws = mutableListOf(MorphProbe.chartDraws)
        val clip = MorphProbe.clip!!
        val grown = mutableListOf(clip.grown)
        compose.runOnIdle { model.back() }
        for (t in 0 until 640 step 16) {
            compose.mainClock.advanceTimeBy(16); frames += frame(); qs += MorphProbe.chartQ; draws += MorphProbe.chartDraws; grown += clip.grown
        }
        compose.mainClock.advanceTimeBy(3000)
        val rest = frame()
        compose.mainClock.autoAdvance = true
        val area = Rect(card.left + 6f, card.top + card.height * 0.5f, card.right - 6f, card.bottom - 6f)
        val mask = frames[lastDrawFor(qs, draws)].let { f -> val a = pixels(f); val b = pixels(rest); Bitmap.createBitmap(f.width, f.height, Bitmap.Config.ARGB_8888).also { d -> d.setPixels(IntArray(a.size) { if (diff(a[it], b[it]) > 24) 0xFFFF0000.toInt() else 0xFF000000.toInt() }, 0, d.width, 0, 0, d.width, d.height) } }
        save("line-closing", listOf(frames[lastDrawFor(qs, draws)], rest, mask), area, 3, 1)
        val lastDraw = draws.indices.last { it == 0 || draws[it] != draws[it - 1] }
        val settledAt = grown.indices.first { grown[it] <= 0.0001f }
        val report = (0 until frames.size).joinToString("\n") { i -> "$i q=${qs[i]} grown=${grown[i]} diff=${differing(frames[i], rest, area, 24).first}" }
        println("ROUND24 line closing: last frame with the travelling line $lastDraw, container home at $settledAt\n$report")
        File("build/screens/default").mkdirs()
        File("build/screens/default/round24-line-closing.txt").writeText("last frame with the travelling line $lastDraw, container home at $settledAt\n$report")
        // From the frame the container is home, the card's line is the settled one.
        val worst = (maxOf(settledAt, lastDraw)..frames.lastIndex).maxOf { differing(frames[it], rest, area, 24).first }
        val atLanding = differing(frames[lastDraw], rest, area, 24).first
        scenario.close()
        assertTrue("the line changed after the card landed: $atLanding px differ in the frame of the travelling line's end ($lastDraw), the container was home at $settledAt\n$report", atLanding <= 150 && worst <= 150)
    }

    /** The same close by a back swipe (predictive back): the page shrinks with the gesture, then closes into the card; after it lands, nothing moves. */
    @Test fun closingTheWeightCardByABackSwipeLeavesItsLineAsItIsWhenTheCardLands() {
        val scenario = launch()
        weighIns(365)
        compose.runOnIdle { model.setOption("ui.measurementRange", "365"); model.tab = Tab.Home }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
        scrollTo("home:weight")
        val card = cardRect("home:weight")
        run({ compose.onNodeWithTag("card:home:weight", useUnmergedTree = true).performTouchInput { click(Offset(8f, 8f)) } })
        assertTrue("the card did not open a page", model.stack.isNotEmpty())
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val clip = MorphProbe.clip!!
        val frames = mutableListOf<Bitmap>(); val qs = mutableListOf<Float>(); val draws = mutableListOf<Int>(); val grown = mutableListOf<Float>()
        fun grab() { frames += frame(); qs += MorphProbe.chartQ; draws += MorphProbe.chartDraws; grown += clip.grown }
        scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 1000f, 0f, androidx.activity.BackEventCompat.EDGE_LEFT)) }
        for (i in 1..12) {
            scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(i * 20f, 1000f, i / 12f, androidx.activity.BackEventCompat.EDGE_LEFT)) }
            compose.mainClock.advanceTimeBy(16); grab()
        }
        val beforeCommit = frames.size
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        for (t in 0 until 800 step 16) { compose.mainClock.advanceTimeBy(16); grab() }
        compose.mainClock.advanceTimeBy(3000)
        val rest = frame()
        compose.mainClock.autoAdvance = true
        val area = Rect(card.left + 6f, card.top + card.height * 0.5f, card.right - 6f, card.bottom - 6f)
        val lastDraw = draws.indices.last { it == 0 || draws[it] != draws[it - 1] }
        val settledAt = grown.indices.first { it >= beforeCommit && grown[it] <= 0.0001f }
        val report = frames.indices.joinToString(NL) { i -> "$i q=${qs[i]} grown=${grown[i]} diff=${differing(frames[i], rest, area, 24).first}" }
        File("build/screens/default").mkdirs()
        File("build/screens/default/round24-line-swipe-closing.txt").writeText("commit at $beforeCommit, last travelling frame $lastDraw, container home at $settledAt$NL$report")
        save("line-swipe-closing", listOf(20, 24, 28, 32, 36, 40, 43).map { frames[(beforeCommit + it - 12).coerceIn(0, frames.lastIndex)] } + rest, Rect(card.left - 40f, card.top - 40f, card.right + 40f, card.bottom + 40f), 4, 1)
        val worst = ((maxOf(settledAt, lastDraw) + 1)..frames.lastIndex).maxOfOrNull { differing(frames[it], rest, area, 24).first } ?: 0
        scenario.close()
        assertTrue("the line moved after the card landed: worst $worst px (commit $beforeCommit, last travelling $lastDraw, home $settledAt)$NL$report", worst <= 150)
    }
    private val NL = "\n"

    @Test fun aDensePagesWiggleIsGoneFromTheTravellingLineBeforeTheCardLands() {
        for (q in listOf(0f, 0.01f, 0.05f, 0.08f)) assertEquals("no wiggle at $q", 0f, pageDetailShare(q), 0f)
        for (q in listOf(0.4f, 0.7f, 1f)) assertEquals("all of the page's own line at $q", 1f, pageDetailShare(q), 0f)
        var before = 0f
        for (i in 0..100) { val v = pageDetailShare(i / 100f); assertTrue("steps up smoothly", v >= before); before = v }
        val zigzag = FloatArray(240) { 100f + (if (it % 2 == 0) 20f else -20f) }
        val smooth = smoothedLine(zigzag)
        assertTrue("the wiggle is averaged away", smooth.max() - smooth.min() < 6f)
        assertEquals(50f, smoothedLine(FloatArray(40) { 50f }).let { it[0] }, 1e-4f)
        assertEquals(zigzag.size, smooth.size)
    }

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private val names = listOf(
        "Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)", "Overhead Press (Barbell)", "Bicep Curl (Dumbbell)",
        "Triceps Pushdown (Cable)", "Romanian Deadlift (Barbell)", "Lateral Raise (Dumbbell)",
    )
    private val bench = names[0]

    /** A profile with thirty weeks of three workouts a week, one in the week still running, a height and three kinds of measurement. */
    private fun launchRich(unit: String = "kg", measurements: Boolean = true): ActivityScenario<MainActivity> {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Rohan")
            profileId = id
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            val rows = ArrayList<WorkoutRow>()
            val first = LocalDate.now().minusWeeks(30)
            var n = 0
            fun add(day: LocalDate, hour: Int) {
                n++
                val date = day.atTime(hour, 0).format(STRONG_DATE_FORMAT)
                val name = listOf("Push", "Pull", "Legs")[n % 3]
                for (e in 0 until 4) {
                    val exercise = names[(n + e * 3) % names.size]
                    rows += WorkoutRow(n.toString(), date, name, "3900", exercise, "W", "20.0", "10", "", "", "", "", "")
                    for (s in 1..3) rows += WorkoutRow(n.toString(), date, name, "3900", exercise, s.toString(), decimalText(40.0 + n * 0.5 + e * 5), (8 + s % 3).toString(), "", "", "", "", "")
                }
            }
            for (week in 0 until 30) for (offset in listOf(0L, 2L, 4L)) {
                val day = first.plusWeeks(week.toLong()).plusDays(offset)
                if (day < LocalDate.now()) add(day, 18)
            }
            // The week still running has a workout in it (the Monday, or today when it is Monday), earlier in the day than any other.
            add(clean.core.weekStart(LocalDate.now()), 7)
            repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
            for (t in listOf("Push", "Pull", "Legs")) {
                val blocks = names.take(6).joinToString(",") { "{\"exercise\":\"$it\",\"sets\":[\"\",\"\",\"\"]}" }
                repo.dao.put(TemplateEntity(0, id, t, "[$blocks]"))
            }
            repo.dao.update(Profile(id, "Rohan", unit, 178.0))
            repo.dao.put(ExerciseSetting(id, bench, goal = "100.0x5"))
            if (measurements) {
                repo.dao.insertMeasurements((0 until 4).flatMap { k ->
                    val at = LocalDate.now().minusWeeks(k * 3L).atTime(8, 0).format(STRONG_DATE_FORMAT)
                    listOf(MeasurementEntity(0, id, "waist.csv", at, decimalText(84.0 - k)), MeasurementEntity(0, id, "chest.csv", at, decimalText(101.0 + k * 0.5)))
                })
                repo.dao.insertMeasurements((0 until 30).map { MeasurementEntity(0, id, "weight.csv", LocalDate.now().minusWeeks(it.toLong()).atTime(8, 0).format(STRONG_DATE_FORMAT), decimalText(82.0 - it * 0.1)) })
            }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded && model.workouts.value.isNotEmpty() && model.consistency.value != null }
        settle()
        return scenario
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 2_000) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun root() = compose.onAllNodes(isRoot()).onFirst()
    private fun open(screen: Screen) { go { model.stack.clear(); model.open(screen) }; settle() }
    private fun textCount(text: String) = compose.onAllNodesWithText(text, substring = true, ignoreCase = true, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun tagCount(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun texts(node: SemanticsNode): List<String> =
        (node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + node.children.flatMap { texts(it) }

    private fun peek(tag: String): List<String>? =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { texts(it) }

    /** Looks for something with [find]; when it is not on screen, scrolls each vertical list of the page to item 1, 2, 3 ... until it is. */
    private fun <T> scrollFind(find: () -> T): T {
        runCatching { return find() }
        val lists = compose.onAllNodes(SemanticsMatcher("lazy list") { it.config.contains(SemanticsActions.ScrollToIndex) }, useUnmergedTree = true).fetchSemanticsNodes().sortedByDescending { it.boundsInRoot.height }
        for (index in 1..12) {
            for (list in lists) {
                val moved = runCatching { compose.runOnIdle { list.config[SemanticsActions.ScrollToIndex].action!!.invoke(index) } }
                if (moved.isFailure) continue
                settle(400)
                runCatching { return find() }
            }
        }
        return find()
    }

    /** Scrolls the page until [text] is on it. */
    /** Waits (in real time: the figures of a page are made off the main thread) for [text], then scrolls the page until it is on it. */
    private fun see(text: String) {
        repeat(60) { if (textCount(text) > 0) return; Thread.sleep(100); compose.waitForIdle() }
        scrollFind { check(textCount(text) > 0) { "no '$text' on the page; texts: " + compose.onAllNodes(SemanticsMatcher("t") { it.config.contains(SemanticsProperties.Text) }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { texts(it) }.distinct().take(60) } }
    }

    private fun boundsOfText(text: String): Rect { see(text); return scrollFind {
        compose.onAllNodes(hasText(text, substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.firstOrNull { it.width > 0f && it.top >= 0f && it.top < 1700f }
            ?: throw AssertionError("no text '$text' on screen")
    } }

    private fun holdAt(at: Offset, tag: String): List<String> {
        root().performTouchInput { down(at) }
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        return peek(tag) ?: throw AssertionError("no card '$tag' after a hold at $at")
    }

    private fun release() { root().performTouchInput { up() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(400); compose.waitForIdle() }

    private fun shot(name: String, dialogs: Boolean = false) {
        settle(300)
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        if (dialogs) {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val roots = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
            for (root in roots) if (root !== view && root.width > 0) root.draw(canvas)
        }
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun look(theme: String) {
        go { model.setOption(clean.core.LOOK_THEME_KEY, theme) }
        repeat(200) { if (model.look.value.dark == (theme == "dark")) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.look.value.dark == (theme == "dark")) { "the look did not change to $theme" }
    }

    private fun theme(name: String) {
        go { model.setOption(clean.core.LOOK_ACCENT_KEY, name) }
        repeat(200) { if (model.look.value.themeName == name) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.look.value.themeName == name) { "the theme did not change to $name" }
    }

    private val top: Screen? get() = model.stack.lastOrNull()


    // ---- Round 24 ----

    private fun allText() = compose.onAllNodes(SemanticsMatcher("any text") { it.config.contains(SemanticsProperties.Text) }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { texts(it) }

    @Test fun thePrTimelineIsGroupedByMonthWithChipsAndHowMuchEachRecordRose() {
        val s = launchRich()
        open(Screen.PrTimeline(bench))
        val rows = prRows(model.workouts.value, model.prs.value).filter { it.exercise == bench }
        assertTrue("the seed has no PRs of $bench", rows.size >= 2)
        val newest = YearMonth.parse(rows.last().date.take(7))
        see(newest.year.toString())
        see(PrKind.entries.first { it in rows.last().kinds }.label)
        assertTrue("no gain said: ${allText().take(40)}", textCount(" +") > 0 || textCount("First record") > 0)
        // a hold peeks at the workout; a tap opens it
        val at = boundsOfText(prettyDay(rows.last().date))
        val card = holdAt(at.center, "workout peek")
        assertTrue("no workout in the peek: $card", card.isNotEmpty())
        release()
        compose.onAllNodesWithText(prettyDay(rows.last().date), substring = true).onFirst().performClick(); settle(600)
        assertTrue("$top", top is Screen.WorkoutDetail)
        s.close()
    }

    @Test fun whatEachPrRoseByIsWorkedOutPerKindAndSide() {
        fun row(date: String, w: Double, r: Int, kinds: Set<PrKind>) =
            PrRow(date, "X", clean.core.SetItem(WorkoutRow("1", date, "A", "1", "X", "1", w.toString(), r.toString(), "", "", "", "", ""), clean.core.SetType.NORMAL), kinds)
        val rows = listOf(
            row("2026-01-01 10:00:00", 100.0, 5, setOf(PrKind.WEIGHT, PrKind.REPS)),
            row("2026-02-01 10:00:00", 105.0, 5, setOf(PrKind.WEIGHT)),
            row("2026-03-01 10:00:00", 105.0, 6, setOf(PrKind.REPS)),
        )
        val gains = prImprovements(rows) { r, k -> if (k == PrKind.WEIGHT) r.set.weight else r.set.reps?.toDouble() }
        assertNull("the first record has nothing before it", gains[0][PrKind.WEIGHT])
        assertEquals(5.0, gains[1][PrKind.WEIGHT]!!, 1e-9)
        assertEquals(1.0, gains[2][PrKind.REPS]!!, 1e-9)
        assertEquals("Weight +5 kg", gainText(PrKind.WEIGHT, 5.0, "kg"))
        assertEquals("Reps +1 rep", gainText(PrKind.REPS, 1.0, "kg"))
        assertEquals("Reps +2 reps", gainText(PrKind.REPS, 2.0, "kg"))
        assertEquals("Weight −2.5 kg", gainText(PrKind.WEIGHT, -2.5, "kg"))
    }

    @Test fun theInfoTabHasMuscleChipsAndAGentleNotesPrompt() {
        val s = launchRich()
        open(Screen.ExerciseDetail(bench, tab = 0))
        see("Main muscles")
        see(model.musclesOf(bench).primary.first().label)
        see("No notes yet")
        see("Goal")
        s.close()
    }

    @Test fun theYearInReviewHasMonthBarsRankedRowsAShareButtonAndBestStreak() {
        val s = launchRich()
        open(Screen.YearReview(LocalDate.now().year))
        see("Volume by month"); see("Most trained"); see("Best streak")
        assertEquals("the old word is gone", 0, textCount("Longest streak"))
        compose.onNodeWithText("Share").assertExists()
        s.close()
    }

    @Test fun theYearSummaryIsPlainText() {
        val totals = clean.core.PeriodTotals(12, 50_000.0, 36_000L, 140.0)
        val r = clean.core.YearReview(2026, totals, listOf(clean.core.LiftGain("Bench Press (Barbell)", 80.0, 90.0)), 3 to 20_000.0, 6, 14.0, "Squat (Barbell)" to 40.0, List(12) { 0.0 }, listOf("Squat (Barbell)" to 40.0, "Bench Press (Barbell)" to 30.0))
        val text = yearSummaryText(r, { "${it.toLong()} kg" }) { "kg" }
        for (line in listOf("2026 in training", "Workouts: 12", "Best streak: 6 weeks in a row", "PRs: 14", "Bench Press (Barbell): 80 to 90 kg", "1. Squat (Barbell), 40 sets", "2. Bench Press (Barbell), 30 sets")) assertTrue("'$line' not in:\n$text", text.contains(line))
    }

    @Test fun aTemplateStartsWithThreeTilesAndExplainsItsArrows() {
        val s = launchRich()
        open(Screen.TemplateStats(model.templates.value.first().id))
        for (word in listOf("Exercises", "Sets", "Done")) see(word)
        see("compares the estimated 1RM")
        see("level")
        s.close()
    }

    @Test fun thePersonalBestsPageHasNoSearchFieldAndRanksItsLifts() {
        val s = launchRich()
        open(Screen.PersonalBests)
        see("Best lifts")
        assertEquals("the search field is gone", 0, textCount("Search exercises"))
        s.close()
    }

    @Test fun trophiesDimTheUnearnedWithProgressAndAWeekOpensItsPage() {
        val s = launchRich()
        open(Screen.Trophies)
        see("Best streak"); see("to go")
        assertEquals(0, textCount("Longest streak"))
        val weekCell = SemanticsMatcher("week cell") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Week of") } == true }
        scrollFind { compose.onAllNodes(weekCell, useUnmergedTree = true).fetchSemanticsNodes().also { check(it.isNotEmpty()) } }
        compose.onAllNodes(weekCell, useUnmergedTree = true).onFirst().performClick(); settle(600)
        assertTrue("$top", top is Screen.Week)
        s.close()
    }

    @Test fun emptyPagesSayWhatBelongsThere() {
        val s = launchRich()
        open(Screen.Routine("No such workout")); see("No workouts yet")
        open(Screen.NoteReview()); see("No notes to review")
        open(Screen.Month(YearMonth.now().minusMonths(60).toString())); see("No workouts in")
        val unlogged = "Face Pull (Cable)"
        open(Screen.ExerciseDetail(unlogged, tab = 2)); see("Nothing to chart yet")
        open(Screen.ExerciseDetail(unlogged, tab = 3)); see("No records yet")
        s.close()
    }

    @Test fun aboutShowsTheLatestChangeAndShowAllOpensTheRest() {
        val s = launchRich()
        go { model.stack.clear() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
        go { model.settingsPage = PAGE_ABOUT; model.open(Screen.Settings) }; settle()
        see("What's new")
        val versions = Regex("^[0-9]+[.][0-9]+[.][0-9]+( .*)?$")
        assertEquals("only the latest release is shown: ${allText().filter { versions.matches(it) }}", 1, allText().count { versions.matches(it) })
        assertTrue("no way to show the rest", textCount("Show all") > 0)
        s.close()
    }

    // ---- The tour ----

    private fun tour(prefix: String) {
        fun s(name: String, step: () -> Unit) {
            try { step(); shot("round24-$prefix-$name") } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("round24-$prefix-$name.failed.txt").writeText(e.toString()) }
        }
        val unlogged = "Face Pull (Cable)"
        s("01-pr-timeline") { open(Screen.PrTimeline(bench)); repeat(10) { Thread.sleep(100); compose.waitForIdle() } }
        s("02-pr-timeline-lower") { root().performTouchInput { swipeUp() } }
        s("03-exercise-info") { open(Screen.ExerciseDetail(bench, tab = 0)) }
        s("04-exercise-info-lower") { root().performTouchInput { swipeUp() } }
        s("05-charts-empty") { open(Screen.ExerciseDetail(unlogged, tab = 2)) }
        s("06-history-tiles") { open(Screen.ExerciseDetail(bench, tab = 1)) }
        s("07-year-review") { open(Screen.YearReview(LocalDate.now().year)); repeat(6) { Thread.sleep(300); compose.waitForIdle(); compose.mainClock.advanceTimeBy(300) } }
        s("08-year-review-lower") { root().performTouchInput { swipeUp() } }
        s("09-template") { open(Screen.TemplateStats(model.templates.value.first().id)) }
        s("10-template-lower") { root().performTouchInput { swipeUp() } }
        s("11-personal-bests") { open(Screen.PersonalBests); repeat(6) { Thread.sleep(300); compose.waitForIdle() } }
        s("12-trophies") { open(Screen.Trophies) }
        s("13-trophies-badges") { root().performTouchInput { swipeUp() }; root().performTouchInput { swipeUp() } }
        s("14-routine-empty") { open(Screen.Routine("No such workout")) }
        s("15-notes-empty") { open(Screen.NoteReview()) }
        s("16-month-empty") { open(Screen.Month(YearMonth.now().minusMonths(60).toString())) }
        s("17-about") { go { model.stack.clear(); model.settingsPage = PAGE_ABOUT; model.open(Screen.Settings) }; settle() }
    }

    @Category(Tour::class) @Test fun tourInGraphiteDarkAndLight() {
        val s = launchRich()
        look("dark"); theme("Graphite")
        tour("graphite-dark")
        look("light"); theme("Graphite")
        tour("graphite-light")
        s.close()
    }
}
