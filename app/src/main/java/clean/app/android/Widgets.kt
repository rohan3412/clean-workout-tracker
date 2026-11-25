// Kept in package clean.app although the file is with the other Android code: the home-screen widgets the user has placed are stored by these class names.
package clean.app


import clean.core.time.*
import android.graphics.drawable.Icon
import android.content.pm.ShortcutManager
import android.content.pm.ShortcutInfo
import android.app.PendingIntent
import android.net.Uri
import android.view.View
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.os.Build
import clean.app.android.AppIcon
import clean.app.android.AndroidPlatform
import clean.app.data.CleanDb
import clean.app.data.open
import clean.core.DayMark
import clean.core.PickKind
import clean.core.ShortcutKind
import clean.core.decodeArgb
import clean.core.shortcutsShown
import clean.core.weekMarks
import clean.core.TemplateRef
import clean.core.Workout
import clean.core.decodeProgram
import clean.core.upNextTemplate
import clean.core.overallName
import clean.core.todayPlan
import clean.core.buildWorkouts
import clean.core.consistency
import clean.core.decodeTargets
import clean.core.targetsOrDefault
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking

/** What the home-screen widgets are filled from: the active profile's workouts and templates, read straight from the database. */
class WidgetData(
    val workouts: List<Workout>,
    /** Id, name and folder. */
    val templates: List<Triple<Long, String, String>>,
    val inProgress: Boolean,
    /** The weekly target as stored (kv `goal.week:<profileId>`). */
    val goal: String? = null,
    /** The program as stored (kv `program:<profileId>`). */
    val program: String? = null,
    /** The overall exercise the user stored for an exercise, by exercise name. */
    val overalls: Map<String, String> = emptyMap(),
    /** The look's colours as stored (kv `widget.colors`, see [TodayColours]). */
    val colours: String? = null,
    /** Whether the Current split switch is on (kv `split.on`). */
    val splitOn: Boolean = false,
    /** The night set of the colours when the theme is "phone" (kv `widget.colors.dark`), else null. */
    val coloursNight: String? = null,
) {
    companion object {
        fun load(context: Context): WidgetData = runBlocking {
            ensureFormats(context)
            val dao = CleanDb.open(context).dao()
            val id = dao.get("active")?.toLongOrNull() ?: return@runBlocking WidgetData(emptyList(), emptyList(), false)
            WidgetData(
                buildWorkouts(dao.rowsNow(id).map { it.toRow() }), dao.templatesNow(id).map { Triple(it.id, it.name, it.folder) }, dao.get("draft:$id") != null, dao.get("goal.week:$id"),
                dao.get("program:$id"), dao.settingsNow(id).filter { it.overall.isNotEmpty() }.associate { it.name to it.overall },
                dao.get("widget.colors"), dao.get("split.on") == "on", dao.get("widget.colors.dark"),
            )
        }
    }
}

private fun ago(day: String): String = when (val days = ChronoUnit.DAYS.between(LocalDate.parse(day), LocalDate.now())) {
    0L -> "today"
    1L -> "yesterday"
    else -> "$days days ago"
}

/**
 * A home-screen widget: three lines of text on a dark tile. Each kind says what its lines are and
 * what a tap does (an action for [AppModel.widgetAction]). They are redrawn when a workout is
 * saved, deleted, started or cancelled, and every half hour.
 */
abstract class CleanWidget : AppWidgetProvider() {
    /** Title, main line, small line, and the action a tap sends to the app. */
    abstract fun content(data: WidgetData): List<String>

    /** Draws this widget on each of [ids] from [data], which the caller has already read. */
    open fun render(context: Context, manager: AppWidgetManager, ids: IntArray, data: WidgetData) {
        val (title, main, small, action) = content(data)
        val views = RemoteViews(context.packageName, R.layout.widget)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_main, main)
        views.setTextViewText(R.id.widget_small, small)
        val open = Intent(context, MainActivity::class.java).putExtra("widget", action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, javaClass.name.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        for (id in ids) manager.updateAppWidget(id, views)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetMidnight.schedule(context)
        val pending = goAsync()
        Thread {
            try {
                render(context, manager, ids, WidgetData.load(context))
            } catch (e: Exception) {
                // A widget that cannot be filled keeps what it showed before.
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onEnabled(context: Context) = WidgetMidnight.schedule(context)

    override fun onDisabled(context: Context) {
        if (!anyWidget(context)) WidgetMidnight.cancel(context)
    }
}

/** This week's workouts against the weekly target and the streak of weeks that met it, as the Profile tab has them; opens the week's report. */
class WeekWidget : CleanWidget() {
    override fun content(data: WidgetData): List<String> {
        val today = LocalDate.now()
        val c = consistency(data.workouts, targetsOrDefault(decodeTargets(data.goal), data.workouts, today), today)
        return listOf("This week", "${c.now.count} of ${c.now.target}", "${c.streak.length} week streak", "week")
    }
}

/** Starts an empty workout, or goes back to the one in progress. */
class StartWidget : CleanWidget() {
    override fun content(data: WidgetData) =
        listOf("Clean", if (data.inProgress) "Resume workout" else "Start workout", if (data.inProgress) "one is in progress" else "an empty one", "start")
}

/** The last workout done; opens it. */
class LastWidget : CleanWidget() {
    override fun content(data: WidgetData): List<String> {
        val last = data.workouts.lastOrNull() ?: return listOf("Last workout", "None yet", "", "")
        return listOf("Last workout", last.name, "${ago(last.day)} · ${duration(last.durationSeconds)}", "last")
    }
}

/** The program's next training day when there is a program, else the template that has waited longest; a tap starts it. */
class NextWidget : CleanWidget() {
    override fun content(data: WidgetData) = upNext(data)
}

/** Title, name, small line and action of what comes next, as the Today card works it out; also what the Today widget and the Start shortcut name. */
internal fun upNext(data: WidgetData): List<String> {
    val today = LocalDate.now()
    val versions = decodeProgram(data.program)
    val plan = if (versions.isEmpty()) null else todayPlan(versions, data.workouts, today) { name -> overallName(name) { data.overalls[it] } }
    val pick = plan?.candidates?.firstOrNull()
    if (pick != null) {
        val day = weekdayName(pick.date.dayOfWeek.value - 1)
        val note = when (pick.kind) {
            PickKind.TODAY -> "today"
            PickKind.EARLIER -> "still to do from $day"
            PickKind.LATER, PickKind.NEXT_WEEK -> day
        }
        val exists = data.templates.any { it.first == pick.day.templateId }
        return listOf("Up next · program", pick.day.label, note, if (exists) "template:${pick.day.templateId}" else "")
    }
    // See [upNextTemplate]: with the Current split switch on, the rotation through that folder; off, the template done longest ago among those used lately.
    val next = upNextTemplate(data.templates.map { TemplateRef(it.first, it.second, it.third) }, data.workouts, data.splitOn, CURRENT_SPLIT, today)
        ?: return listOf("Up next", "No template yet", "", "")
    return listOf(if (next.fromSplit) "Up next · current split" else "Up next", next.template.name, next.reason, "template:${next.template.id}")
}

/** The look's colours the Today widget is drawn in, kept in kv `widget.colors` (page, raised, text, muted, accent, on accent: ARGB) so a widget redrawn with the app gone still has them. */
class TodayColours(val page: Int, val raised: Int, val text: Int, val muted: Int, val accent: Int, val onAccent: Int) {
    companion object {
        /** The dark tile the other widgets use, until the app has stored the look. */
        private val DARK = TodayColours(0xFF121316.toInt(), 0xFF1E2025.toInt(), 0xFFF2F3F5.toInt(), 0xFFA0A4AE.toInt(), 0xFF8AB4FF.toInt(), 0xFF000000.toInt())
        fun of(stored: String?): TodayColours = decodeArgb(stored, 6)?.let { TodayColours(it[0], it[1], it[2], it[3], it[4], it[5]) } ?: DARK

        /** The night set stored beside the day one with the theme "phone"; null when there is none. */
        fun ofNight(stored: String?): TodayColours? = decodeArgb(stored, 6)?.let { TodayColours(it[0], it[1], it[2], it[3], it[4], it[5]) }
    }

    fun list() = listOf(page, raised, text, muted, accent, onAccent)
}

private fun withAlpha(colour: Int, alpha: Int) = (colour and 0x00FFFFFF) or (alpha shl 24)

/**
 * Today's template and a Start button, the seven days of the week and the week streak, in the colours of the look (stored by the
 * app whenever the look changes). The slot is the one the Today card shows; with a workout running the button reads Resume.
 */
class TodayWidget : CleanWidget() {
    /** Unused: this widget draws its own layout. */
    override fun content(data: WidgetData): List<String> = emptyList()

    private val dayIds = listOf(
        R.id.day0_letter to R.id.day0_dot, R.id.day1_letter to R.id.day1_dot, R.id.day2_letter to R.id.day2_dot, R.id.day3_letter to R.id.day3_dot,
        R.id.day4_letter to R.id.day4_dot, R.id.day5_letter to R.id.day5_dot, R.id.day6_letter to R.id.day6_dot,
    )

    override fun render(context: Context, manager: AppWidgetManager, ids: IntArray, data: WidgetData) {
        val views = build(context, data)
        for (id in ids) manager.updateAppWidget(id, views)
    }

    /** The widget as it is drawn from [data]. */
    internal fun build(context: Context, data: WidgetData): RemoteViews {
        ensureFormats(context)
        val colours = TodayColours.of(data.colours)
        // With the theme "phone" a night set is stored too; from Android 12 the launcher picks the set itself when the phone's mode changes.
        val night = if (Build.VERSION.SDK_INT >= 31) TodayColours.ofNight(data.coloursNight) else null
        val next = upNext(data)
        val today = LocalDate.now()
        val running = data.inProgress
        val action = if (running) "workout" else next[3].ifEmpty { "start" }
        val streak = consistency(data.workouts, targetsOrDefault(decodeTargets(data.goal), data.workouts, today), today).streak.length
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        /** Sets a colour through [method] of the view, as [colour] picks it from a set (the night set too when there is one). */
        fun paint(id: Int, method: String, colour: (TodayColours) -> Int) {
            if (night != null && Build.VERSION.SDK_INT >= 31) views.setColorInt(id, method, colour(colours), colour(night)) else views.setInt(id, method, colour(colours))
        }
        paint(R.id.today_bg, "setColorFilter") { it.page }
        paint(R.id.today_pill, "setColorFilter") { it.accent }
        views.setTextViewText(R.id.today_title, if (running) "In progress" else "Today")
        paint(R.id.today_title, "setTextColor") { it.muted }
        views.setTextViewText(R.id.today_streak, "$streak wk streak")
        paint(R.id.today_streak, "setTextColor") { it.accent }
        views.setContentDescription(R.id.today_streak, "$streak week streak")
        views.setTextViewText(R.id.today_name, if (running) "Workout" else next[1])
        paint(R.id.today_name, "setTextColor") { it.text }
        views.setTextViewText(R.id.today_note, if (running) "one is in progress" else next[2])
        paint(R.id.today_note, "setTextColor") { it.muted }
        views.setTextViewText(R.id.today_start_label, if (running) "Resume" else "Start")
        paint(R.id.today_start_label, "setTextColor") { it.onAccent }
        val trained = data.workouts.mapTo(HashSet()) { it.day }
        for ((i, mark) in weekMarks(today, trained).withIndex()) {
            val (letter, dot) = dayIds[i]
            val day = weekdayName(i)
            views.setTextViewText(letter, day.take(1))
            // The dot, its letter, whether it is a ring, and the word for the day.
            val ring = mark == DayMark.TODAY || mark == DayMark.FUTURE
            val word = when (mark) { DayMark.TRAINED -> "trained"; DayMark.TODAY -> "today"; DayMark.PAST -> "rest"; DayMark.FUTURE -> "to come" }
            views.setImageViewResource(dot, if (ring) R.drawable.widget_ring else R.drawable.widget_dot)
            paint(dot, "setColorFilter") {
                when (mark) { DayMark.TRAINED, DayMark.TODAY -> it.accent; DayMark.PAST -> withAlpha(it.muted, 0x55); DayMark.FUTURE -> withAlpha(it.muted, 0x70) }
            }
            paint(letter, "setTextColor") {
                when (mark) { DayMark.TRAINED -> it.text; DayMark.TODAY -> it.accent; DayMark.PAST -> it.muted; DayMark.FUTURE -> withAlpha(it.muted, 0x90) }
            }
            views.setContentDescription(letter, "$day, $word")
        }
        val open = Intent(context, MainActivity::class.java).putExtra("widget", action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, javaClass.name.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.today_root, tap)
        views.setOnClickPendingIntent(R.id.today_start, tap)
        views.setContentDescription(R.id.today_start, if (running) "Resume workout" else "Start ${next[1]}".takeIf { next[3].isNotEmpty() } ?: "Start workout")
        return views
    }
}

/** The last seven days as dots, filled on the days with a workout; opens the calendar. */
class DaysWidget : CleanWidget() {
    override fun content(data: WidgetData): List<String> {
        val days = data.workouts.mapTo(HashSet()) { it.day }
        val week = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()).toString() in days }
        return listOf("Last 7 days", week.joinToString(" ") { if (it) "●" else "○" }, "${week.count { it }} of 7 days trained", "calendar")
    }
}

/** How long ago the last workout was, for a nudge; opens the app. */
class RestDaysWidget : CleanWidget() {
    override fun content(data: WidgetData): List<String> {
        val last = data.workouts.lastOrNull() ?: return listOf("Since last workout", NONE, "", "")
        val days = ChronoUnit.DAYS.between(LocalDate.parse(last.day), LocalDate.now())
        return listOf("Since last workout", if (days == 0L) "Trained today" else "$days ${if (days == 1L) "day" else "days"}", last.name, "last")
    }
}

private val WIDGETS: List<CleanWidget> = listOf(WeekWidget(), StartWidget(), LastWidget(), NextWidget(), DaysWidget(), RestDaysWidget(), TodayWidget())

/**
 * The launcher icon's shortcuts: Start, Resume (while a workout runs) and Log weight. Start names the template the Today card
 * would start, Resume shows only while a workout runs, Log bodyweight opens the weight ruler. The links are the app's own, `clean://`.
 */
private fun applyShortcuts(context: Context, data: WidgetData) {
    val next = upNext(data)
    val named = next[3].isNotEmpty()
    fun open(link: Uri?, action: String?): Intent = Intent(Intent.ACTION_VIEW, link, context, MainActivity::class.java).also { if (action != null) it.putExtra("widget", action) }
    // Shortcuts name the launcher activity they belong to: the icon alias that is enabled now (AppIcon), since a disabled alias takes its shortcuts with it.
    val activity = AppIcon.enabledComponent(context)
    fun build(kind: ShortcutKind, rank: Int): ShortcutInfo {
        val (short, long, intent) = when (kind) {
            ShortcutKind.START -> Triple(if (named) next[1].take(24) else "Start workout", if (named) "Start ${next[1]}".take(40) else "Start workout", open(null, if (named) next[3] else "start"))
            ShortcutKind.RESUME -> Triple("Resume workout", "Resume workout", open(Uri.parse("clean://resume"), null))
            ShortcutKind.WEIGHT -> Triple("Log bodyweight", "Log bodyweight", open(Uri.parse("clean://weight"), null))
        }
        return ShortcutInfo.Builder(context, kind.key).setShortLabel(short).setLongLabel(long).setRank(rank).setActivity(activity)
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher)).setIntent(intent).build()
    }
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return
    manager.dynamicShortcuts = shortcutsShown(data.inProgress).mapIndexed { rank, kind -> build(kind, rank) }
    // Shortcuts the user has pinned to the home screen are moved to the same activity.
    runCatching {
        val pinned = manager.pinnedShortcuts.map { it.id }.toSet()
        val again = ShortcutKind.entries.filter { it.key in pinned }.map { build(it, 0) }
        if (again.isNotEmpty()) manager.updateShortcuts(again)
    }
}

/** Redraws every widget on the home screen and brings the launcher shortcuts up to date (this is also what a change of look, of the shortcuts chosen or of the next template goes through); the data is read once for all of them. */
fun refreshWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    Thread {
        runCatching {
            val data = WidgetData.load(context)
            runCatching { applyShortcuts(context, data) }
            WidgetMidnight.schedule(context)
            if (manager != null) for (widget in WIDGETS) {
                val ids = manager.getAppWidgetIds(ComponentName(context, widget.javaClass))
                if (ids.isNotEmpty()) widget.render(context, manager, ids, data)
            }
        }
    }.start()
}

/** Whether any widget of Clean is on the home screen. */
private fun anyWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    return WIDGETS.any { manager.getAppWidgetIds(ComponentName(context, it.javaClass)).isNotEmpty() }
}

/**
 * The widgets say "today", so they are drawn again just after midnight and not up to half an hour later: one inexact alarm
 * (no permission needed) for the next local midnight and a minute, set again by [WidgetMidnightReceiver] each time it goes off,
 * when a widget is placed or redrawn, and after a restart or a change of clock or time zone. Taken back when no widget is left.
 */
object WidgetMidnight {
    private const val FIRE = "clean.app.WIDGET_MIDNIGHT"

    private fun pending(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, FIRE.hashCode(), Intent(context, WidgetMidnightReceiver::class.java).setAction(FIRE), flags or PendingIntent.FLAG_IMMUTABLE)

    /** The next midnight and a minute after [now], in clock milliseconds. */
    internal fun nextMillis(now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Long =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1).toInstant().toEpochMilli()

    /** Sets the alarm, when a widget is on the home screen; takes it back when none is. */
    fun schedule(context: Context) {
        if (!anyWidget(context)) { cancel(context); return }
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        pending(context, PendingIntent.FLAG_UPDATE_CURRENT)?.let { runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC, nextMillis(), it) } }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        pending(context, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
    }
}

/** The midnight alarm: redraws the widgets and sets the next one. Not exported; Android stores it by this class name. */
class WidgetMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            refreshWidgets(context)
            WidgetMidnight.schedule(context)
        }
    }
}

/**
 * The widgets' weekday names and dates come through the app's [Formats.platform], which only the running app sets; a widget redrawn in a
 * process the app is not running in (an update from the launcher, the midnight alarm) would otherwise find it unset and stay as it was.
 */
internal fun ensureFormats(context: Context) {
    if (!Formats.ready) Formats.platform = AndroidPlatform(context.applicationContext as android.app.Application)
}
