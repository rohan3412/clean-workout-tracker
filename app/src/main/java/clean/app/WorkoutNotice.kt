package clean.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * The workout in progress as a quiet, ongoing notification while the app is behind (Settings, Workout, "Workout on the lock screen",
 * kv `workout.notif`): its name, the time since it started counting up, and a line with the rest (when it ends) and the next set.
 * Public on the lock screen, like the rest's countdown; a tap opens the workout. Nothing here goes online.
 */
object WorkoutNotice {
    private const val ID = 7003
    private const val CHANNEL = "workout_running"

    fun post(context: Context, name: String, startMillis: Long, text: String?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val open = Intent(context, MainActivity::class.java).putExtra("widget", "workout").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        manager.notify(
            ID,
            Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_rest)
                .setContentTitle(name.ifBlank { "Workout" }).setContentText(text)
                .setWhen(startMillis).setShowWhen(true).setUsesChronometer(true)
                .setContentIntent(PendingIntent.getActivity(context, 4, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setOnlyAlertOnce(true)
                .build(),
        )
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID)
}
