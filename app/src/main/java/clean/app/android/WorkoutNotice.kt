package clean.app

import clean.app.android.*


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import clean.app.platform.NoticeRest

/**
 * The workout in progress as a quiet, ongoing notification while the app is behind (Settings, Workout, "Workout on the lock screen",
 * kv `workout.notif`): its name, the time since it started counting up, and a line with the rest (when it ends) and the next set. A rest carried by an alarm is shown in it (countdown, next set, "+15 s", "Skip") when it is on.
 * Public on the lock screen, like the rest's countdown; a tap opens the workout. "Set done" on it ticks the next set (N16, round 7) as the
 * check on the card would, and starts the rest. Nothing here goes online.
 */
object WorkoutNotice {
    private const val ID = 7003
    private const val CHANNEL = "workout_running"
    internal const val SET_DONE = "clean.app.SET_DONE"

    /** The running app's own way of ticking the next set (AppModel.tickNextSet); null when the app is not alive, and the tap then starts it. */
    var onSetDone: (() -> Unit)? = null

    /**
     * [setDone] says whether there is a set left to tick; the button is only there when there is. With [rest] (a rest carried by an alarm while
     * the app is behind) the notification shows that rest in place of a separate one: a countdown to its end, the next set, and "+15 s" and "Skip".
     */
    fun post(context: Context, name: String, startMillis: Long, text: String?, setDone: Boolean = false, rest: NoticeRest? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val open = Intent(context, MainActivity::class.java).putExtra("widget", "workout").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // An activity, not a broadcast: with the app gone a receiver could only start the app from a notification action, which Android 12 and later block.
        val tick = Intent(context, MainActivity::class.java).putExtra("widget", "setdone").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val builder = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_rest)
                .setContentTitle(name.ifBlank { "Workout" })
                .setContentIntent(PendingIntent.getActivity(context, 4, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setOnlyAlertOnce(true)
        if (rest != null) {
            // The chronometer counts down to the end of the rest, as the rest notification's own does.
            builder.setContentText(listOfNotNull("Rest", rest.next).joinToString(" · "))
                .setWhen(rest.end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .addAction(RestAlert.button(context, "+${RestAlert.MORE} s", RestAlert.ADD, rest.end, rest.key, rest.total, rest.next))
                .addAction(RestAlert.button(context, "Skip", RestAlert.SKIP, rest.end, rest.key, rest.total, rest.next))
        } else {
            builder.setContentText(text).setWhen(startMillis).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(false)
            if (setDone) builder.addAction(
                Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Set done", PendingIntent.getActivity(context, 7, tick, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build(),
            )
        }
        manager.notify(ID, builder.build())
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID)
}

/**
 * "Set done" on the workout notification now opens the app with the action `setdone`, which ticks once the workout has been read back
 * (AppModel.widgetAction). It used to be a broadcast to this receiver, which started the app when the process was gone; Android 12 and later
 * block that (a notification trampoline). The receiver stays for notifications posted before the change that are still on the screen:
 * with the app alive it ticks as before, otherwise it does nothing.
 */
class WorkoutNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WorkoutNotice.SET_DONE) return
        WorkoutNotice.onSetDone?.let { it() }
    }
}
