package clean.app

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.open
import androidx.compose.ui.semantics.getOrNull
import clean.core.PhotoFilter
import clean.core.Pose
import clean.core.momentOf
import clean.core.photoName
import clean.core.photosSummary
import clean.core.time.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 39: one Photos section. Profile's fan shows the newest four pictures of both kinds with a line of figures under it, one Photos page holds the progress photos and
 * the snapshots (filters, labels, Compare for progress photos only, one Add that asks what the photo is), and the two stores on disk are as they were.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1100dp-mdpi")
class Round39Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun pause(times: Int = 4) { repeat(times) { Thread.sleep(100); compose.waitForIdle() } }

    private fun launch() {
        runBlocking {
            val repo = Repo(CleanDb.open(context))
            for (key in listOf("photos.blur", "photos.lock", "ui.photo.pose", "ui.photo.kind")) repo.dao.remove(key)
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", "off"))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
    }

    /** Waits for [done], giving the main looper time between looks (a flow that hops dispatchers needs it). */
    private fun until(done: () -> Boolean) { repeat(100) { if (!done()) { Thread.sleep(100); compose.waitForIdle() } }; assertTrue("waited 10 s", done()) }
    private fun has(text: String) = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun chip(text: String) = compose.onAllNodes(hasText(text) and hasClickAction()).onLast()
    private fun labels(): List<String> = compose.onAllNodes(hasTestTag("pic label"), useUnmergedTree = true).fetchSemanticsNodes()
        .map { (it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString("") { t -> t.text }).orEmpty() }

    private fun jpeg(file: File, colour: Int) {
        file.parentFile!!.mkdirs()
        val bmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawColor(colour)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun daysAgo(days: Long, hour: Int = 9) = LocalDateTime.now().withHour(hour).withMinute(0).withSecond(0).withNano(0).minusDays(days)

    /** Three progress photos (two front, one side) and three snapshots on three days; the newest four are two of each kind. */
    private fun seed(): Pair<List<File>, List<File>> {
        val id = model.activeId.value!!
        val photos = listOf(20L to Pose.FRONT, 2L to Pose.FRONT, 2L to Pose.SIDE).map { (d, p) ->
            PhotoFiles.file(context, id, photoName(daysAgo(d), p)).also { jpeg(it, 0xFF3B5BA5.toInt()) }
        }
        val moments = listOf(Triple(0L, 8, "00000001"), Triple(0L, 7, "00000002"), Triple(2L, 8, "00000003")).map { (d, h, k) ->
            MomentFiles.file(context, id, momentOf(daysAgo(d, h), k).name).also { jpeg(it, 0xFF8A4F7D.toInt()) }
        }
        go { model.refreshPhotos(); model.refreshMoments() }
        until { model.pics.value.size == 6 }
        return photos to moments
    }

    private fun toProfile() { go { model.stack.clear(); model.tab = Tab.Profile }; settle(400); pause() }

    private fun toFan() {
        toProfile()
        go { kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Profile).scrollToItem(2) } }
        settle(600); pause()
    }

    private fun snap(): Bitmap {
        compose.waitForIdle()
        val view = window!!
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }

    private fun shot(name: String) {
        settle(300); pause()
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { snap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openPhotos(filter: PhotoFilter = PhotoFilter.ALL) {
        go { model.stack.clear(); model.open(if (filter == PhotoFilter.SNAPSHOTS) Screen.Moments else Screen.Photos) }
        settle(600); pause()
    }

    // ---- 1 and 2. The fan and the line under it ----

    @Test fun theFanShowsTheNewestFourOfBothKindsUnderTheHeadingPhotos() {
        launch(); seed()
        val newest = model.pics.value.take(4)
        assertEquals(listOf(true, true, false, false), newest.map { it.snapshot })
        toFan()
        assertTrue("the heading is Photos", has("Photos"))
        assertFalse("no Snapshots heading is left", has("Snapshots"))
        assertTrue("the fan names its count", compose.onAllNodes(hasContentDescription("Photos, 6 photos"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        shot("round39-profile-fan")
    }

    @Test fun theLineBelowTheFanSaysHowManyHowManyAreProgressSinceWhenAndLast() {
        launch(); seed()
        toFan()
        val expected = photosSummary(model.pics.value, LocalDateTime.now())
        assertTrue("6 photos, 3 progress, last today: $expected", expected.startsWith("6 photos · 3 progress · since "))
        assertTrue(expected.endsWith("last 1 day ago") || expected.contains("last "))
        assertTrue("the line is on Profile: $expected", has(expected))
        assertTrue("and the span of the front photos", has("2 weeks between your first and latest front photo"))
    }

    @Test fun tappingTheFanOrTheLineOpensPhotos() {
        launch(); seed()
        toFan()
        compose.onAllNodes(hasTestTag("card:profile:moments"), useUnmergedTree = true).onFirst().performClick(); settle(900)
        assertEquals(Screen.Photos, model.stack.lastOrNull())
        go { model.back() }; settle(900)
        compose.onAllNodes(hasTestTag("photos stats"), useUnmergedTree = true).onFirst().performClick(); settle(900)
        assertEquals(Screen.Photos, model.stack.lastOrNull())
    }

    @Test fun thereIsNoProgressStripOnProfileAnyMore() {
        launch(); seed()
        toProfile()
        for (i in 0 until 12) {
            go { kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Profile).scrollToItem(i) } }
            settle(200)
            assertFalse("no strip with a Compare shortcut", has("Compare"))
            assertFalse("no 'Add a photo' line", has("Add a photo"))
        }
    }

    @Test fun withNoPicturesTheFanHasOneAddPhotoPillThatStartsTheAddFlow() {
        launch()
        toFan()
        assertFalse("no figures", has("photos ·"))
        compose.onAllNodes(hasText("Add photo")).onFirst().performScrollTo().performClick(); settle(); pause()
        assertTrue(has("Take a photo")); assertTrue(has("Choose from the phone"))
        shot("round39-add-first-sheet")
    }

    // ---- 3. The page ----

    @Test fun theFiltersShowAllProgressSnapshotsAndThePoses() {
        launch(); seed()
        openPhotos()
        assertEquals("all six", 6, labels().size)
        shot("round39-photos-all")
        chip("Progress").performClick(); settle(300); pause()
        assertEquals(listOf("Front", "Front", "Side").sorted(), labels().sorted())
        assertTrue("the pose row appears under Progress", has("All poses"))
        shot("round39-photos-progress")
        chip("Front").performClick(); settle(300); pause()
        assertEquals(listOf("Front", "Front"), labels())
        shot("round39-photos-front")
        chip("Side").performClick(); settle(300); pause()
        assertEquals(listOf("Side"), labels())
        chip("Snapshots").performClick(); settle(300); pause()
        assertEquals(listOf("Snapshot", "Snapshot", "Snapshot"), labels())
        assertFalse("no pose row under Snapshots", has("All poses"))
        chip("All").performClick(); settle(300); pause()
        assertEquals(6, labels().size)
    }

    @Test fun everyPictureCarriesItsLabel() {
        launch(); seed()
        openPhotos()
        assertEquals(listOf("Front", "Front", "Side", "Snapshot", "Snapshot", "Snapshot").sorted(), labels().sorted())
    }

    @Test fun theOldMomentsRouteOpensThePageOnSnapshots() {
        launch(); seed()
        openPhotos(PhotoFilter.SNAPSHOTS)
        assertEquals(listOf("Snapshot", "Snapshot", "Snapshot"), labels())
    }

    @Test fun compareIsOfferedForProgressPhotosOfOnePoseOnly() {
        launch(); seed()
        openPhotos()
        assertFalse("not under All", has("Compare"))
        chip("Snapshots").performClick(); settle(300); pause()
        assertFalse("not under Snapshots", has("Compare"))
        chip("Progress").performClick(); settle(300); pause()
        assertTrue("under Progress", has("Compare"))
        chip("Side").performClick(); settle(300); pause()
        assertFalse("not for a pose with one photo", has("Compare"))
        chip("Front").performClick(); settle(300); pause()
        assertTrue("for a pose with two", has("Compare"))
        compose.onAllNodes(hasText("Compare") and hasClickAction()).onFirst().performClick(); settle(600)
        assertEquals(Screen.PhotoCompare(Pose.FRONT), model.stack.lastOrNull())
    }

    @Test fun theViewerPagesOverBothKindsAndTurnsASnapshotIntoAProgressPhotoAndBack() {
        launch(); seed()
        val snapshot = model.pics.value.first { it.snapshot }
        var moved: clean.core.Pic? = null
        go { model.changePic(snapshot, snapshot.at, Pose.BACK) { moved = it } }
        until { moved != null || model.message != null }
        assertNotNull("message: ${model.message}", moved)
        assertEquals(Pose.BACK, moved!!.pose)
        val id = model.activeId.value!!
        assertTrue("a file in the photos folder", PhotoFiles.file(context, id, moved!!.name).exists())
        assertFalse("and none left in the snapshots folder", MomentFiles.file(context, id, snapshot.name).exists())
        var again: clean.core.Pic? = null
        go { model.changePic(moved!!, moved!!.at, null) { again = it } }
        until { again != null }
        assertTrue(again!!.snapshot)
        assertTrue(MomentFiles.file(context, id, again!!.name).exists())
        assertFalse(PhotoFiles.file(context, id, moved!!.name).exists())
        // The viewer pages over a filter and labels each picture by its kind (opened last: a file that is open cannot be renamed on Windows).
        val other = model.pics.value.first { it.snapshot && it.name != again!!.name }
        go { model.open(Screen.Photo(other.name)) }; settle(600); pause()
        assertTrue("the snapshot's label is in the viewer", has("${prettyDay(other.day)} · Snapshot"))
    }

    // ---- The add flow ----

    private fun waitForPics(n: Int) = until { model.pics.value.size == n }

    @Test fun addingAsksWhatThePhotoIsThenFilesItInTheRightStore() {
        launch(); seed()
        val id = model.activeId.value!!
        openPhotos()
        // The plus in the bar offers the camera and the phone.
        compose.onAllNodes(hasContentDescription("Add photo")).onFirst().performClick(); settle(); pause()
        assertTrue(has("Take a photo")); assertTrue(has("Choose from the phone"))
        shot("round39-add")
        go { model.photoAsker = "page"; model.photoPending = listOf(File(context.cacheDir, "pick.jpg").also { jpeg(it, 0xFF4F8A6B.toInt()) }.path) to false }
        settle(); pause()
        assertTrue("then it asks what it is", has("What is it?"))
        assertTrue(has("Progress")); assertTrue(has("Snapshot")); assertTrue("a pose for progress", has("POSE"))
        shot("round39-add-kind")
        chip("Side").performClick(); settle(200)
        compose.onAllNodes(hasText("Add") and hasClickAction()).onLast().performClick(); settle(); pause()
        waitForPics(7)
        assertEquals("remembered as the last pose", "side", model.options.value["ui.photo.pose"])
        assertEquals(2, PhotoFiles.list(context, id).count { it.pose == Pose.SIDE })
        assertEquals(3, MomentFiles.list(context, id).size)
        // As a snapshot: no pose is asked, and it goes to the snapshots folder.
        go { model.photoPending = listOf(File(context.cacheDir, "pick2.jpg").also { jpeg(it, 0xFF9A4D4D.toInt()) }.path) to false }
        settle(); pause()
        chip("Snapshot").performClick(); settle(200)
        assertFalse("no pose for a snapshot", has("POSE"))
        compose.onAllNodes(hasText("Add") and hasClickAction()).onLast().performClick(); settle(); pause()
        waitForPics(8)
        assertEquals(4, MomentFiles.list(context, id).size)
        assertEquals(4, PhotoFiles.list(context, id).size)
    }

    @Test fun theEmptyPageOffersTheSameAdd() {
        launch()
        openPhotos()
        assertTrue(has("No photos yet"))
        shot("round39-empty")
        compose.onAllNodes(hasText("Add photo") and hasClickAction()).onFirst().performClick(); settle(); pause()
        assertTrue(has("Take a photo"))
    }

    // ---- 5. Blur and lock cover both ----

    @Test fun blurCoversBothKindsInEverySmallView() {
        launch(); val (photos, moments) = seed()
        go { model.setOption("photos.blur", "on") }
        toFan()
        until { PHOTO_THUMBS.get(thumbKey(photos[1].path, 360, true)) != null && MOMENT_THUMBS.get(thumbKey(moments[0].path, 360, true)) != null }
        assertNull("no sharp progress thumbnail", PHOTO_THUMBS.get(thumbKey(photos[1].path, 360, false)))
        assertNull("no sharp snapshot thumbnail", MOMENT_THUMBS.get(thumbKey(moments[0].path, 360, false)))
    }


    @Test fun lockShutsBothKindsToTheBlurWhateverTheSettingSays() {
        launch(); val (photos, moments) = seed()
        go { model.setOption("photos.lock", "on") }
        until { model.photosShut }
        toFan()
        until { PHOTO_THUMBS.get(thumbKey(photos[1].path, 360, true)) != null && MOMENT_THUMBS.get(thumbKey(moments[0].path, 360, true)) != null }
        assertNull(PHOTO_THUMBS.get(thumbKey(photos[1].path, 360, false)))
        assertNull(MOMENT_THUMBS.get(thumbKey(moments[0].path, 360, false)))
    }

    // ---- 4. Storage ----

    @Test fun theBackupCarriesBothFoldersUnchanged() {
        launch(); val (photos, moments) = seed()
        val id = model.activeId.value!!
        val out = ByteArrayOutputStream()
        Backup.write(context, out, withAnimations = false)
        val opened = BackupFormat.open(out.toByteArray())
        val inside = opened.files.associate { it.first to it.second }
        for (p in photos) assertTrue("photos/$id/${p.name}", inside["photos/$id/${p.name}"]!!.contentEquals(p.readBytes()))
        for (m in moments) assertTrue("moments/$id/${m.name}", inside["moments/$id/${m.name}"]!!.contentEquals(m.readBytes()))
        assertEquals("nothing else under those folders", 6, inside.keys.count { it.startsWith("photos/") || it.startsWith("moments/") })
        // The names are the old ones: each folder still lists only its own kind.
        assertEquals(3, PhotoFiles.list(context, id).size)
        assertEquals(3, MomentFiles.list(context, id).size)
    }


    @Category(Tour::class) @Test fun tourOfTheOnePhotosSection() {
        launch(); seed()
        toFan(); shot("round39-tour-profile")
        openPhotos(); shot("round39-tour-all")
    }

}
