package clean.core

import kotlinx.datetime.LocalDateTime

/**
 * What core needs from the platform and cannot do in common Kotlin. The jvmMain actuals are the code core always ran;
 * the iosMain ones are written for iOS.
 */

/** One file of a zip. */
class ZipItem(val name: String, val bytes: ByteArray) {
    val isDirectory: Boolean get() = name.endsWith("/")
}

/** The entries of the zip in [bytes], in file order; only those whose name [wanted] says yes to are read, the rest are skipped. */
expect fun readZipItems(bytes: ByteArray, wanted: (String) -> Boolean = { true }): List<ZipItem>

/** [items] as a zip, every entry stamped [modifiedMillis] (milliseconds since the epoch). */
expect fun writeZipItems(items: List<ZipItem>, modifiedMillis: Long): ByteArray

/** The wall clock now, in the phone's time zone. */
expect fun currentLocalDateTime(): LocalDateTime

/** A map that many threads read and fill at once, for caches whose values are worked out once per key. */
expect class ConcurrentCache<K : Any, V : Any>() {
    operator fun get(key: K): V?
    operator fun set(key: K, value: V)
    /** The value for [key], worked out by [make] and stored when there is none yet. [make] may run more than once under a race. */
    fun getOrPut(key: K, make: () -> V): V
}
