@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package clean.core

import kotlin.concurrent.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitNanosecond
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

actual fun currentLocalDateTime(): LocalDateTime {
    val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or
        NSCalendarUnitMinute or NSCalendarUnitSecond or NSCalendarUnitNanosecond
    val c = NSCalendar.currentCalendar.components(units, fromDate = NSDate())
    return LocalDateTime(c.year.toInt(), c.month.toInt(), c.day.toInt(), c.hour.toInt(), c.minute.toInt(), c.second.toInt(), c.nanosecond.toInt())
}

actual class ConcurrentCache<K : Any, V : Any> actual constructor() {
    private val map = HashMap<K, V>()
    private val lock = AtomicInt(0)

    private inline fun <T> locked(block: () -> T): T {
        while (!lock.compareAndSet(0, 1)) { /* spin: the sections below are a lookup or an insert */ }
        try { return block() } finally { lock.value = 0 }
    }

    actual operator fun get(key: K): V? = locked { map[key] }
    actual operator fun set(key: K, value: V) { locked { map[key] = value } }
    actual fun getOrPut(key: K, make: () -> V): V {
        locked { map[key] }?.let { return it }
        val made = make() // outside the lock: make may use the cache itself
        return locked { map.getOrPut(key) { made } }
    }
}
