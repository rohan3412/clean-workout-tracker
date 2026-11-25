package clean.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

actual fun readZipItems(bytes: ByteArray, wanted: (String) -> Boolean): List<ZipItem> {
    val items = mutableListOf<ZipItem>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!wanted(entry.name)) continue
            items += ZipItem(entry.name, zip.readBytes())
        }
    }
    return items
}

actual fun writeZipItems(items: List<ZipItem>, modifiedMillis: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val zip = ZipOutputStream(output)
    for (item in items) {
        zip.putNextEntry(ZipEntry(item.name).apply { time = modifiedMillis })
        zip.write(item.bytes)
        zip.closeEntry()
    }
    zip.finish()
    return output.toByteArray()
}

actual fun currentLocalDateTime(): LocalDateTime = java.time.LocalDateTime.now().toKotlinLocalDateTime()

actual class ConcurrentCache<K : Any, V : Any> actual constructor() {
    private val map = ConcurrentHashMap<K, V>()
    actual operator fun get(key: K): V? = map[key]
    actual operator fun set(key: K, value: V) { map[key] = value }
    actual fun getOrPut(key: K, make: () -> V): V = map.getOrPut(key, make)
}
