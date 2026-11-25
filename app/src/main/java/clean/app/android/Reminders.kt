package clean.app

import clean.app.android.*


import clean.core.time.*
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import clean.app.data.CleanDao
import clean.app.data.CleanDb
import clean.app.data.open
import clean.core.REMIND_DEFAULT_TIME
import clean.core.nextReminder
import clean.core.parseClock
import clean.core.parseRemindDays
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime

/**
 * Workout-day reminders: on the weekdays chosen in Settings, Workout (kv `remind.days`, none by default, so off) at the time chosen
 * (kv `remind.time`, `HH:mm`, 18:00 unless set), a notification says it is a workout day, unless a workout was already logged that day.
 * One inexact alarm is set at a time, for the next reminder; it is set again when it goes off, when the choice changes, when the app
 * starts and after the phone restarts. All of it is on the phone.
 */
object WorkoutReminder {
    private const val FIRE = "clean.app.REMIND"
    private const val CHANNEL = "workout_reminder"
    private const val ID = 7004

    private fun pending(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, FIRE.hashCode(), Intent(context, ReminderReceiver::class.java).setAction(FIRE), flags or PendingIntent.FLAG_IMMUTABLE)

    /** Sets the alarm for the next reminder from [days] (kv text) and [time] (kv text), or takes it back when no day is chosen. */
    fun sync(context: Context, days: String?, time: String?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = nextReminder(LocalDateTime.now(), parseRemindDays(days), parseClock(time) ?: REMIND_DEFAULT_TIME)
        if (at == null) {
            pending(context, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
            return
        }
        val millis = at.toJavaLocalDateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact is enough for a reminder, and needs no permission of its own.
        pending(context, PendingIntent.FLAG_UPDATE_CURRENT)?.let { runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, it) } }
    }

    internal fun isFire(intent: Intent) = intent.action == FIRE

    /**
     * The reminder going off: posts it unless a workout was already logged today, a workout is running now (its draft is stored) or no day is
     * chosen any more. True when it was posted.
     */
    internal suspend fun remindIfDue(context: Context, dao: CleanDao): Boolean {
        val profile = dao.get("active")?.toLongOrNull()
        val trained = profile != null && dao.rowCountOn(profile, LocalDate.now().toString()) > 0
        val running = profile != null && dao.get("draft:$profile") != null
        if (trained || running || parseRemindDays(dao.get("remind.days")).isEmpty()) return false
        post(context, WidgetData.load(context))
        return true
    }

    /** Says it is a workout day, naming what is next as the Today card does when [data] is given ("Up next: Push"), with a Start button for it. */
    internal fun post(context: Context, data: WidgetData? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val next = data?.let { upNext(it) }
        val named = next != null && next[3].isNotEmpty()
        val builder = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Workout day").setContentText(if (named) "Up next: ${next!![1]}" else "Today is one of your training days.")
            .setContentIntent(PendingIntent.getActivity(context, 5, open, flags))
            .setAutoCancel(true)
        if (next != null) {
            // The same action the Today widget's Start button sends.
            val start = Intent(context, MainActivity::class.java).putExtra("widget", next[3].ifEmpty { "start" }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            builder.addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Start", PendingIntent.getActivity(context, 6, start, flags)).build())
        }
        manager.notify(ID, builder.build())
    }
}

/**
 * The reminder's alarm, and the phone restarting, the app being updated, or the clock or time zone changing (alarms do not survive the first
 * two, and the other two move the hour). Reads the options from the database, as the widgets do. A failure in the background never takes the app down.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                runBlocking {
                    val dao = CleanDb.open(context).dao()
                    if (WorkoutReminder.isFire(intent)) WorkoutReminder.remindIfDue(context, dao)
                    WorkoutReminder.sync(context, dao.get("remind.days"), dao.get("remind.time"))
                }
                // A new clock or time zone moves midnight, and the widgets' "today" with it.
                if (!WorkoutReminder.isFire(intent)) { WidgetMidnight.schedule(context); refreshWidgets(context) }
            } catch (e: Exception) {
                // A database that cannot be read is not worth a crash of the app in the background.
            } finally {
                pending.finish()
            }
        }.start()
    }
}
