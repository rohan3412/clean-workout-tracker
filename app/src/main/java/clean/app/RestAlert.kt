package clean.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.RemoteViews
import clean.app.data.CleanDao
import clean.app.data.CleanDb
import clean.core.restEndFeedback
import clean.core.restNotifCompact
import kotlinx.coroutines.runBlocking

/**
 * A rest that ends while the app is behind or the screen is off: two exact alarms, one five seconds
 * before the end for the buzzes and one at the end for the bell, and a quiet notification counting
 * down meanwhile. All of it is on the phone; nothing here goes online.
 */
object RestAlert {
    private const val COUNTDOWN = "clean.app.REST_COUNTDOWN"
    private const val END = "clean.app.REST_END"
    internal const val ADD = "clean.app.REST_ADD"
    internal const val SKIP = "clean.app.REST_SKIP"
    internal const val TOGGLE = "clean.app.REST_TOGGLE"
    /** Seconds the notification's "+15 s" adds. */
    internal const val MORE = 15
    /** The running app's own way of changing its rest (true adds [MORE] seconds, false skips); null when the app is not alive. */
    var onAction: ((Boolean) -> Unit)? = null
    private const val RUNNING_ID = 7001
    private const val OVER_ID = 7002
    /** A channel's sound cannot be changed once it is made, so each sound has a channel of its own; the first one, from before there was a choice, is taken away. */
    private const val OLD_BELL_CHANNEL = "rest_over"
    private fun bellChannel(tone: RestTone) = "rest_over_${tone.key}"
    private const val QUIET_CHANNEL = "rest_running"

    private fun alarmFor(context: Context, action: String, end: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, action.hashCode(), Intent(context, RestAlertReceiver::class.java).setAction(action).putExtra("end", end), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_BELL_CHANNEL)
        for (tone in RestTone.entries) manager.createNotificationChannel(
            NotificationChannel(bellChannel(tone), "Rest over: ${tone.label}", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(Uri.parse("android.resource://${context.packageName}/${tone.raw}"), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(NotificationChannel(QUIET_CHANNEL, "Rest in progress", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
    }

    private fun builder(context: Context, channel: String): Notification.Builder {
        val open = Intent(context, MainActivity::class.java).putExtra("widget", "workout").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_rest)
            .setContentIntent(PendingIntent.getActivity(context, 3, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    private fun set(alarms: AlarmManager, at: Long, pending: PendingIntent?) {
        if (pending == null) return
        // From Android 12 an exact alarm may need the user's say-so (Android 13 and later grant it to USE_EXACT_ALARM); without it, an inexact one.
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        runCatching {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** A button on the countdown notification; it carries the rest's end and where the rest is stored, for when the app is gone. */
    private fun button(context: Context, title: String, action: String, end: Long, key: String?, total: Int, next: String?): Notification.Action {
        val intent = Intent(context, RestAlertReceiver::class.java).setAction(action).putExtra("end", end).putExtra("key", key).putExtra("total", total).putExtra("next", next)
        return Notification.Action.Builder(null as android.graphics.drawable.Icon?, title, PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    }

    /**
     * Sets the alarms for a rest ending at [end] (clock milliseconds) and shows the countdown in a notification,
     * with "+15 s" and "Skip" on it so the rest can be changed from the lock screen. [key] is the kv key the rest is kept under,
     * [total] the rest's length in seconds and [next] the next set in words; the buttons carry them for when the app is gone.
     */
    fun schedule(context: Context, end: Long, key: String? = null, total: Int = 0, next: String? = null, compact: Boolean = false) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        set(alarms, maxOf(System.currentTimeMillis(), end - 5000), alarmFor(context, COUNTDOWN, end, PendingIntent.FLAG_UPDATE_CURRENT))
        set(alarms, end, alarmFor(context, END, end, PendingIntent.FLAG_UPDATE_CURRENT))
        post(context, end, key, total, next, compact)
    }

    /**
     * The notification alone: silent and ongoing, public on the lock screen (it holds only the countdown and the next set). Its own
     * layout is the larger form (countdown, how far the rest has gone, the next set, a chevron at the bottom right) or the smaller one
     * (countdown, chevron at the top right). The bar is set when this is posted and when the rest changes, never every second.
     */
    private fun post(context: Context, end: Long, key: String?, total: Int, next: String?, compact: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        channels(context)
        val left = (end - System.currentTimeMillis()).coerceAtLeast(0)
        val toggle = Intent(context, RestAlertReceiver::class.java).setAction(TOGGLE).putExtra("end", end).putExtra("key", key).putExtra("total", total).putExtra("next", next)
        val flip = PendingIntent.getBroadcast(context, TOGGLE.hashCode(), toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // The collapsed view is always the one-line form, since the shade shows a silent notification collapsed and the larger
        // layout does not fit there. In the larger form the system's own arrow opens it, so the one-line chevron is hidden.
        val small = RemoteViews(context.packageName, R.layout.notif_rest_compact)
        small.setChronometer(R.id.notif_clock, SystemClock.elapsedRealtime() + left, null, true)
        small.setChronometerCountDown(R.id.notif_clock, true)
        small.setViewVisibility(R.id.notif_toggle, if (compact) android.view.View.VISIBLE else android.view.View.GONE)
        small.setContentDescription(R.id.notif_toggle, "Show more")
        small.setOnClickPendingIntent(R.id.notif_toggle, flip)
        val large = if (compact) null else RemoteViews(context.packageName, R.layout.notif_rest_expanded).apply {
            setChronometer(R.id.notif_clock, SystemClock.elapsedRealtime() + left, null, true)
            setChronometerCountDown(R.id.notif_clock, true)
            val done = if (total > 0) (1000 - left / total).toInt().coerceIn(0, 1000) else 0
            setProgressBar(R.id.notif_progress, 1000, done, false)
            setTextViewText(R.id.notif_next, next ?: "")
            setViewVisibility(R.id.notif_next, if (next == null) android.view.View.INVISIBLE else android.view.View.VISIBLE)
            setContentDescription(R.id.notif_toggle, "Show less")
            setOnClickPendingIntent(R.id.notif_toggle, flip)
        }
        manager.notify(
            RUNNING_ID,
            builder(context, QUIET_CHANNEL).setContentTitle("Rest").setContentText(next).setCustomContentView(small).apply { if (large != null) setCustomBigContentView(large) }.setStyle(Notification.DecoratedCustomViewStyle())
                .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .addAction(button(context, "+$MORE s", ADD, end, key, total, next)).addAction(button(context, "Skip", SKIP, end, key, total, next)).build(),
        )
    }

    /** Draws the notification again in the other form: the chevron was pressed. */
    internal fun repost(context: Context, end: Long, key: String?, total: Int, next: String?, compact: Boolean) {
        if (end > System.currentTimeMillis()) post(context, end, key, total, next, compact)
    }

    /** Takes back the alarms and both notifications: the app is in front again, or the rest is over or skipped. */
    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (action in listOf(COUNTDOWN, END)) alarmFor(context, action, 0, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(RUNNING_ID)
        manager.cancel(OVER_ID)
    }

    /** One buzz pattern for the whole countdown: a pulse a second apart for what is left of the last five seconds, the last one longer. */
    @Suppress("DEPRECATION")
    internal fun countdown(context: Context, end: Long) {
        val left = ((end - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtMost(5)
        if (left < 1) return
        val timings = LongArray(2 * left)
        for (i in 0 until left) {
            val on = if (i == left - 1) 600L else 150L
            timings[2 * i + 1] = on
            if (i > 0) timings[2 * i] = 850L
        }
        val effect = VibrationEffect.createWaveform(timings, -1)
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        // Alarm usage, so the phone does not drop the buzzes because the app is not in front.
        if (Build.VERSION.SDK_INT >= 33) vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        else vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    /** "Rest over", with the sound chosen, or quietly when [tone] is null. */
    internal fun over(context: Context, tone: RestTone?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(RUNNING_ID)
        if (!manager.areNotificationsEnabled()) return
        channels(context)
        manager.notify(
            OVER_ID,
            builder(context, tone?.let { bellChannel(it) } ?: QUIET_CHANNEL).setContentTitle("Rest over").setContentText("Back to your workout.").setAutoCancel(true).setTimeoutAfter(60_000).build(),
        )
    }
}

/** Receives the alarms of [RestAlert]. It reads the options it needs from the database, as the widgets do, since the app may not be running. */
class RestAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The app itself changes its rest when it is alive, on the main thread where it runs.
        if (intent.action == RestAlert.ADD || intent.action == RestAlert.SKIP) RestAlert.onAction?.let { it(intent.action == RestAlert.ADD); return }
        // The rest reads or writes the database, never on the main thread: on a thread of its own, as the widgets do.
        val pending = goAsync()
        Thread {
            try {
                runBlocking { handle(context, intent) }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val dao = CleanDb.open(context).dao()
        when (intent.action) {
            "clean.app.REST_COUNTDOWN" -> if (restEndOf(dao).vibrate) RestAlert.countdown(context, intent.getLongExtra("end", 0))
            RestAlert.ADD, RestAlert.SKIP -> {
                // The app is gone: the alarms and the stored rest are changed here.
                val add = intent.action == RestAlert.ADD
                val key = intent.getStringExtra("key")
                val end = intent.getLongExtra("end", 0) + RestAlert.MORE * 1000L
                RestAlert.cancel(context)
                if (add && end > System.currentTimeMillis()) {
                    val shown = intent.getIntExtra("total", 0) + RestAlert.MORE
                    RestAlert.schedule(context, end, key, shown, intent.getStringExtra("next"), restNotifCompact(dao.get("rest.notif")))
                }
                if (key != null) {
                    val total = dao.get(key)?.substringAfter('|', "")?.toIntOrNull() ?: 0
                    if (add) dao.put(clean.app.data.Kv(key, "$end|${total + RestAlert.MORE}")) else dao.remove(key)
                }
            }
            // The chevron: the other form of the notification, kept for the next rest too.
            RestAlert.TOGGLE -> {
                val compact = !restNotifCompact(dao.get("rest.notif"))
                dao.put(clean.app.data.Kv("rest.notif", if (compact) "compact" else "expanded"))
                RestAlert.repost(context, intent.getLongExtra("end", 0), intent.getStringExtra("key"), intent.getIntExtra("total", 0), intent.getStringExtra("next"), compact)
            }
            "clean.app.REST_END" -> RestAlert.over(context, if (restEndOf(dao).sound) RestTone.of(dao.get("rest.tone")) else null)
        }
    }

    /** How a rest ends, read from the database (the app may not be running). */
    private suspend fun restEndOf(dao: CleanDao) = restEndFeedback(dao.get("feedback.restEnd"), dao.get("rest.sound"), dao.get("rest.alert"))
}
