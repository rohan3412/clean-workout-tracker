package clean.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import clean.app.data.CleanDb
import clean.core.REMIND_DEFAULT_TIME
import clean.core.nextReminder
import clean.core.parseClock
import clean.core.parseRemindDays
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact is enough for a reminder, and needs no permission of its own.
        pending(context, PendingIntent.FLAG_UPDATE_CURRENT)?.let { runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, it) } }
    }

    internal fun isFire(intent: Intent) = intent.action == FIRE

    internal fun post(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        manager.notify(
            ID,
            Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_rest)
                .setContentTitle("Workout day").setContentText("Today is one of your training days.")
                .setContentIntent(PendingIntent.getActivity(context, 5, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true).build(),
        )
    }
}

/** The reminder's alarm, and the phone restarting or the app being updated (alarms do not survive either). Reads the options from the database, as the widgets do. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                runBlocking {
                    val dao = CleanDb.open(context).dao()
                    if (WorkoutReminder.isFire(intent)) {
                        val profile = dao.get("active")?.toLongOrNull()
                        val trained = profile != null && dao.rowCountOn(profile, LocalDate.now().toString()) > 0
                        if (!trained && parseRemindDays(dao.get("remind.days")).isNotEmpty()) WorkoutReminder.post(context)
                    }
                    WorkoutReminder.sync(context, dao.get("remind.days"), dao.get("remind.time"))
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
