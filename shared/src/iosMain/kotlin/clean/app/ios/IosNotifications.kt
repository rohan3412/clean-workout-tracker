@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package clean.app.ios

import clean.app.RestTone
import clean.app.currentMillis
import clean.app.data.CleanDb
import clean.app.data.DB_FILE
import clean.app.data.Kv
import clean.app.data.open
import clean.app.platform.REST_NOTIFICATION_MORE
import clean.core.restEndFeedback
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSDateComponents
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.numberWithLongLong
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * The rest alert, the workout notice and the workout-day reminder, as the phone's local notifications (nothing here goes online).
 * iOS cannot count a notification down, buzz at the last seconds from the background or keep a notification "ongoing"; what Android does
 * with alarms and a live notification is here a notification scheduled for the end of the rest (with the chosen sound), a quiet one
 * in the Notification Center with "+15 s" and "Skip" on it, and a quiet one for the workout with "Set done". Live Activities need a
 * widget extension and are left out.
 */
class IosNotifications(private val host: IosHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    /** Whether notifications are allowed, as last read (the system answers asynchronously). */
    var permitted: Boolean = false
        private set

    private val delegate = Delegate()

    fun install() {
        center.delegate = delegate
        val more = UNNotificationAction.actionWithIdentifier(REST_ADD, "+$REST_NOTIFICATION_MORE s", 0uL)
        val skip = UNNotificationAction.actionWithIdentifier(REST_SKIP, "Skip", 0uL)
        val done = UNNotificationAction.actionWithIdentifier(SET_DONE, "Set done", 0uL)
        center.setNotificationCategories(
            setOf(
                UNNotificationCategory.categoryWithIdentifier(CATEGORY_REST, listOf(more, skip), emptyList<Any>(), 0uL),
                UNNotificationCategory.categoryWithIdentifier(CATEGORY_WORKOUT, listOf(done), emptyList<Any>(), 0uL),
            ),
        )
        refreshPermission()
    }

    fun refreshPermission() {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            permitted = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
        }
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            permitted = granted
            onMain { onResult(granted) }
        }
    }

    // ---- Rest

    private fun content(title: String, body: String?, category: String? = null, quiet: Boolean = false, info: Map<Any?, Any?> = emptyMap()): UNMutableNotificationContent =
        UNMutableNotificationContent().apply {
            setTitle(title)
            if (body != null) setBody(body)
            if (category != null) setCategoryIdentifier(category)
            if (quiet) setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelPassive)
            setUserInfo(info)
        }

    private fun add(id: String, content: UNMutableNotificationContent, trigger: platform.UserNotifications.UNNotificationTrigger?) {
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, trigger), null)
    }

    /** The rest ending at [end] (clock milliseconds): a notification then, with the sound the user chose (none when sound is off), and a quiet one now. */
    fun scheduleRest(end: Long, key: String?, total: Int, next: String?, tone: RestTone?) {
        cancelRest()
        val seconds = ((end - currentMillis()) / 1000.0).coerceAtLeast(1.0)
        val over = content("Rest over", "Back to your workout.", info = mapOf("action" to "workout"))
        if (tone != null) over.setSound(UNNotificationSound.soundNamed(IosFeedback.soundFile(tone)))
        add(REST_OVER, over, UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, false))
        val info = mapOf<Any?, Any?>("action" to "workout", "end" to NSNumber.numberWithLongLong(end), "key" to (key ?: ""), "total" to NSNumber.numberWithLongLong(total.toLong()), "next" to (next ?: ""))
        add(REST_RUNNING, content("Rest", next, CATEGORY_REST, quiet = true, info = info), null)
    }

    fun cancelRest() {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(REST_OVER, REST_RUNNING))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(REST_OVER, REST_RUNNING))
    }

    // ---- The workout in progress

    fun postWorkout(name: String, text: String?, setDone: Boolean) {
        add(WORKOUT, content(name.ifBlank { "Workout" }, text, if (setDone) CATEGORY_WORKOUT else null, quiet = true, info = mapOf("action" to "workout")), null)
    }

    fun cancelWorkout() {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(WORKOUT))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(WORKOUT))
    }

    // ---- Workout-day reminder

    /** One repeating notification a week for each chosen weekday ([isoDays] Monday 1) at [minutes] after midnight; none when no day is chosen. */
    fun syncReminder(isoDays: Set<Int>, minutes: Int) {
        val all = (1..7).map { "$REMIND-$it" }
        center.removePendingNotificationRequestsWithIdentifiers(all)
        for (iso in isoDays) {
            val parts = NSDateComponents()
            parts.weekday = (iso % 7 + 1).toLong()
            parts.hour = (minutes / 60).toLong()
            parts.minute = (minutes % 60).toLong()
            add("$REMIND-$iso", content("Workout day", "Today is one of your training days.", info = mapOf("action" to "open")), UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(parts, true))
        }
    }

    // ---- What the user does with a notification

    private fun long(value: Any?): Long = (value as? NSNumber)?.longLongValue ?: 0L

    private fun respond(action: String, info: Map<Any?, *>) {
        when (action) {
            REST_ADD, REST_SKIP -> {
                val add = action == REST_ADD
                val callback = host.platform.onRestNotificationAction
                if (callback != null) { onMain { callback(add) }; return }
                // The app is gone: the alarm and the stored rest are changed here, as Android's receiver does.
                val key = (info["key"] as? String)?.takeIf { it.isNotEmpty() }
                val end = long(info["end"]) + REST_NOTIFICATION_MORE * 1000L
                cancelRest()
                if (add && end > currentMillis()) {
                    val shown = long(info["total"]).toInt() + REST_NOTIFICATION_MORE
                    scope.launch { scheduleRest(end, key, shown, (info["next"] as? String)?.takeIf { it.isNotEmpty() }, restTone()) }
                }
                if (key != null) scope.launch {
                    val dao = CleanDb.open(Fs.join(Fs.root, DB_FILE)).dao()
                    val total = dao.get(key)?.substringAfter('|', "")?.toIntOrNull() ?: 0
                    if (add) dao.put(Kv(key, "$end|${total + REST_NOTIFICATION_MORE}")) else dao.remove(key)
                }
            }
            SET_DONE -> {
                val callback = host.platform.onSetDoneNotification
                if (callback != null) onMain { callback() } else host.openAction("setdone")
            }
            else -> (info["action"] as? String)?.takeIf { it != "open" }?.let { host.openAction(it) }
        }
    }

    /** The sound the rest ends with, read from the database (the app may not be running), or null when sound is off. */
    suspend fun restTone(): RestTone? {
        val dao = CleanDb.open(Fs.join(Fs.root, DB_FILE)).dao()
        return if (restEndFeedback(dao.get("feedback.restEnd"), dao.get("rest.sound"), dao.get("rest.alert")).sound) RestTone.of(dao.get("rest.tone")) else null
    }

    private inner class Delegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(center: UNUserNotificationCenter, willPresentNotification: UNNotification, withCompletionHandler: (platform.UserNotifications.UNNotificationPresentationOptions) -> Unit) {
            // In front the app counts the rest and shows the workout itself; only the reminder is shown over it.
            val id = willPresentNotification.request.identifier
            withCompletionHandler(if (id.startsWith(REMIND)) UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList else 0uL)
        }

        override fun userNotificationCenter(center: UNUserNotificationCenter, didReceiveNotificationResponse: UNNotificationResponse, withCompletionHandler: () -> Unit) {
            respond(didReceiveNotificationResponse.actionIdentifier, didReceiveNotificationResponse.notification.request.content.userInfo)
            withCompletionHandler()
        }
    }

    companion object {
        private const val CATEGORY_REST = "clean.rest"
        private const val CATEGORY_WORKOUT = "clean.workout"
        private const val REST_ADD = "clean.REST_ADD"
        private const val REST_SKIP = "clean.REST_SKIP"
        private const val SET_DONE = "clean.SET_DONE"
        private const val REST_OVER = "clean.rest.over"
        private const val REST_RUNNING = "clean.rest.running"
        private const val WORKOUT = "clean.workout"
        private const val REMIND = "clean.remind"
    }
}
