package clean.app

import org.junit.experimental.categories.Category
import clean.app.android.*

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.TemplateEntity
import clean.app.data.open
import clean.app.platform.NoticeRest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The peripheral audit's fixes that can be checked without a phone: reminders, the one notification during a rest, midnight, widget colours, the icon switch. */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PeripheralAuditTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    private fun seed(draft: Draft? = null, templates: List<String> = emptyList(), extra: Map<String, String> = emptyMap()): Long {
        CleanDb.close()
        return runBlocking {
            val db = CleanDb.open(context)
            val id = Repo(db).createProfile("Rohan")
            val dao = db.dao()
            dao.put(Kv("active", id.toString()))
            for ((k, v) in extra) dao.put(Kv(k, v))
            for (name in templates) dao.put(TemplateEntity(profileId = id, name = name, blocks = ""))
            if (draft != null) dao.put(Kv("draft:$id", draft.toJson()))
            id
        }
    }

    private val everyDay = mapOf("remind.days" to "1,2,3,4,5,6,7")

    @Test fun `the reminder is skipped while a workout is running`() {
        seed(draft = Draft(name = "Push", startMillis = 1_000L), extra = everyDay)
        val posted = runBlocking { WorkoutReminder.remindIfDue(context, CleanDb.open(context).dao()) }
        assertFalse(posted)
        assertNull(notifications.getNotification(7004))
    }

    @Test fun `the reminder names what is up next and has a Start button`() {
        seed(templates = listOf("Push"), extra = everyDay)
        val posted = runBlocking { WorkoutReminder.remindIfDue(context, CleanDb.open(context).dao()) }
        assertTrue(posted)
        val n = notifications.getNotification(7004)!!
        assertEquals("Workout day", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Up next: Push", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("Start"), n.actions.map { it.title.toString() })
        val start = shadowOf(n.actions[0].actionIntent)
        assertTrue(start.isActivityIntent)
        assertEquals("template:1", start.savedIntent.getStringExtra("widget"))
    }

    @Test fun `without a template the reminder keeps its old sentence`() {
        seed(extra = everyDay)
        runBlocking { WorkoutReminder.remindIfDue(context, CleanDb.open(context).dao()) }
        assertEquals("Today is one of your training days.", notifications.getNotification(7004)!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun `Set done opens the app with the set done action`() {
        WorkoutNotice.post(context, "Push", 1_000L, "Next: Squat · set 1", setDone = true)
        val n = notifications.getNotification(7003)!!
        val pi = shadowOf(n.actions.single().actionIntent)
        assertTrue(pi.isActivityIntent)
        assertEquals("setdone", pi.savedIntent.getStringExtra("widget"))
    }

    @Test fun `a rest shows inside the workout notification with its own buttons and no second notification`() {
        RestAlert.schedule(context, System.currentTimeMillis() + 60_000, "draft:rest:1", 60, "Next: Squat · set 1", inWorkoutNotice = true)
        assertNull(notifications.getNotification(7001))
        WorkoutNotice.post(context, "Push", 1_000L, null, rest = NoticeRest(System.currentTimeMillis() + 60_000, "draft:rest:1", 60, "Next: Squat · set 1"))
        val n = notifications.getNotification(7003)!!
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(listOf("+15 s", "Skip"), n.actions.map { it.title.toString() })
        assertEquals("Rest · Next: Squat · set 1", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        // Not in the workout notice: the rest has a notification of its own, as before.
        RestAlert.schedule(context, System.currentTimeMillis() + 60_000, null, 60, null)
        assertNotNull(notifications.getNotification(7001))
    }

    @Test fun `skipping the rest with the app gone puts the workout notification back to plain`() {
        val id = seed(draft = Draft(name = "Push", startMillis = 1_000L))
        val end = System.currentTimeMillis() + 60_000
        runBlocking { CleanDb.open(context).dao().put(Kv("draft:rest:$id", "$end|60")) }
        WorkoutNotice.post(context, "Push", 1_000L, null, rest = NoticeRest(end, "draft:rest:$id", 60, null))
        val skip = Intent(context, RestAlertReceiver::class.java).setAction(RestAlert.SKIP).putExtra("end", end).putExtra("key", "draft:rest:$id").putExtra("total", 60)
        runBlocking { RestAlertReceiver().handle(context, skip) }
        val n = notifications.getNotification(7003)!!
        assertFalse(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(0, n.actions?.size ?: 0)
        assertNull(runBlocking { CleanDb.open(context).dao().get("draft:rest:$id") })
        assertNull(notifications.getNotification(7001))
    }

    @Test fun `adding time with the app gone keeps the rest in the workout notification`() {
        val id = seed(draft = Draft(name = "Push", startMillis = 1_000L))
        val end = System.currentTimeMillis() + 60_000
        runBlocking { CleanDb.open(context).dao().put(Kv("draft:rest:$id", "$end|60")) }
        val add = Intent(context, RestAlertReceiver::class.java).setAction(RestAlert.ADD).putExtra("end", end).putExtra("key", "draft:rest:$id").putExtra("total", 60)
        runBlocking { RestAlertReceiver().handle(context, add) }
        val n = notifications.getNotification(7003)!!
        assertEquals(listOf("+15 s", "Skip"), n.actions.map { it.title.toString() })
        assertNull(notifications.getNotification(7001))
        assertEquals("${end + 15_000}|75", runBlocking { CleanDb.open(context).dao().get("draft:rest:$id") })
    }

    @Test fun `the midnight alarm is for the next midnight and a minute`() {
        val now = java.time.ZonedDateTime.of(2026, 11, 20, 23, 50, 0, 0, java.time.ZoneId.of("Europe/Amsterdam"))
        val at = java.time.Instant.ofEpochMilli(WidgetMidnight.nextMillis(now)).atZone(now.zone)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 21, 0, 1), at.toLocalDateTime())
    }

    @Test fun `the icon switch says whether it changed anything and names the enabled alias`() {
        assertTrue(AppIcon.applyIcon(context, "Plum"))
        assertFalse(AppIcon.applyIcon(context, "Plum"))
        assertEquals(ComponentName(context.packageName, "clean.app.IconPlum"), AppIcon.enabledComponent(context))
    }

    private fun todayNameColour(): Int {
        val data = WidgetData(emptyList(), emptyList(), false, colours = "-1,-1,-65536,-1,-1,-1", coloursNight = "-1,-1,-16711936,-1,-1,-1")
        val view = TodayWidget().build(context, data).apply(context, android.widget.FrameLayout(context))
        return view.findViewById<TextView>(R.id.today_name).currentTextColor
    }

    @Test fun `the Today widget uses the day colours by day`() {
        assertEquals(-65536, todayNameColour())
    }

    @Test @Config(sdk = [35], qualifiers = "night") fun `the Today widget uses the night colours by night`() {
        assertEquals(-16711936, todayNameColour())
    }
}
