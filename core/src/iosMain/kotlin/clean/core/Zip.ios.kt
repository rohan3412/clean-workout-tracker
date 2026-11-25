@file:OptIn(ExperimentalForeignApi::class)

package clean.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

/*
 * Zip on iOS, by hand over zlib's raw deflate (window bits -15), so the zips are the ones the JVM and Android write and read: STORED and
 * DEFLATED entries, data descriptors, UTF-8 names. Reading goes by the central directory (the sizes are always there, also for entries
 * written with a data descriptor). Writing is what `java.util.zip.ZipOutputStream` writes: DEFLATED, flags 0x0808, the sizes after the
 * data, and the entry's time as local DOS time. Zip64 (4 GiB and over) is not supported.
 */

private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
private fun u32(b: ByteArray, at: Int): Long = (u16(b, at).toLong()) or (u16(b, at + 2).toLong() shl 16)

private fun fail(message: String): Nothing = throw IllegalArgumentException(message)

/** Inflates the raw deflate stream [length] bytes long at [from] in [src] to exactly [size] bytes. */
fun inflateRaw(src: ByteArray, from: Int, length: Int, size: Int): ByteArray {
    val out = ByteArray(size)
    memScoped {
        val stream = alloc<z_stream>()
        stream.zalloc = null
        stream.zfree = null
        stream.opaque = null
        stream.avail_in = 0u
        stream.next_in = null
        if (inflateInit2_(stream.ptr, -15, zlibVersion()!!.toKString(), sizeOf<z_stream>().convert()) != Z_OK) fail("The zip could not be read (inflate)")
        try {
            src.usePinned { input ->
                out.usePinned { output ->
                    stream.next_in = input.addressOf(from).reinterpret()
                    stream.avail_in = length.convert()
                    stream.next_out = output.addressOf(0).reinterpret()
                    stream.avail_out = size.convert()
                    val result = inflate(stream.ptr, Z_FINISH)
                    if (result != Z_STREAM_END || stream.total_out.toLong() != size.toLong()) fail("A zip entry is damaged")
                }
            }
        } finally {
            inflateEnd(stream.ptr)
        }
    }
    return out
}

/** [data] as a raw deflate stream. */
fun deflateRaw(data: ByteArray): ByteArray {
    memScoped {
        val stream = alloc<z_stream>()
        stream.zalloc = null
        stream.zfree = null
        stream.opaque = null
        stream.avail_in = 0u
        stream.next_in = null
        if (deflateInit2_(stream.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, -15, 8, Z_DEFAULT_STRATEGY, zlibVersion()!!.toKString(), sizeOf<z_stream>().convert()) != Z_OK) fail("The zip could not be written (deflate)")
        try {
            val out = ByteArray(deflateBound(stream.ptr, data.size.convert()).toInt() + 16)
            // The pinned arrays need at least one element to take an address of; an empty entry gets a one-byte stand-in that is never read.
            val input = if (data.isEmpty()) ByteArray(1) else data
            input.usePinned { inPin ->
                out.usePinned { outPin ->
                    stream.next_in = inPin.addressOf(0).reinterpret()
                    stream.avail_in = data.size.convert()
                    stream.next_out = outPin.addressOf(0).reinterpret()
                    stream.avail_out = out.size.convert()
                    if (deflate(stream.ptr, Z_FINISH) != Z_STREAM_END) fail("The zip could not be written (deflate)")
                }
            }
            return out.copyOf(stream.total_out.toInt())
        } finally {
            deflateEnd(stream.ptr)
        }
    }
}

private val CRC_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
    c
}

internal fun crc32(data: ByteArray): Int {
    var c = -1
    for (b in data) c = CRC_TABLE[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
    return c.inv()
}

actual fun readZipItems(bytes: ByteArray, wanted: (String) -> Boolean): List<ZipItem> {
    // The end-of-central-directory record is the last 22 bytes plus a comment of up to 65535.
    var end = bytes.size - 22
    val lowest = maxOf(0, bytes.size - 22 - 65535)
    while (end >= lowest && u32(bytes, end) != 0x06054b50L) end--
    if (end < lowest) fail("That file is not a zip")
    val count = u16(bytes, end + 10)
    var at = u32(bytes, end + 16).toInt()
    val items = ArrayList<ZipItem>()
    repeat(count) {
        if (at + 46 > bytes.size || u32(bytes, at) != 0x02014b50L) fail("The zip's directory is damaged")
        val method = u16(bytes, at + 10)
        val packed = u32(bytes, at + 20).toInt()
        val size = u32(bytes, at + 24).toInt()
        val nameLength = u16(bytes, at + 28)
        val extraLength = u16(bytes, at + 30)
        val commentLength = u16(bytes, at + 32)
        val local = u32(bytes, at + 42).toInt()
        val name = bytes.decodeToString(at + 46, at + 46 + nameLength)
        at += 46 + nameLength + extraLength + commentLength
        if (!wanted(name)) return@repeat
        if (local + 30 > bytes.size || u32(bytes, local) != 0x04034b50L) fail("The zip's directory is damaged")
        val start = local + 30 + u16(bytes, local + 26) + u16(bytes, local + 28)
        if (start + packed > bytes.size) fail("The zip is cut short")
        items += ZipItem(
            name,
            when (method) {
                0 -> bytes.copyOfRange(start, start + packed)
                8 -> inflateRaw(bytes, start, packed, size)
                else -> fail("A zip entry uses a compression this app does not read")
            },
        )
    }
    return items
}

/** A byte buffer that grows by doubling, for writing a zip of many megabytes. */
private class Out {
    var data = ByteArray(1 shl 16)
    var size = 0
    private fun room(more: Int) {
        if (size + more > data.size) data = data.copyOf(maxOf(data.size * 2, size + more))
    }
    fun put(value: Long, bytes: Int) {
        room(bytes)
        for (i in 0 until bytes) data[size++] = ((value ushr (8 * i)) and 0xFF).toByte()
    }
    fun put(bytes: ByteArray) {
        room(bytes.size)
        bytes.copyInto(data, size)
        size += bytes.size
    }
}

actual fun writeZipItems(items: List<ZipItem>, modifiedMillis: Long): ByteArray {
    // The DOS time is the phone's clock reading, as `ZipEntry.setTime` writes it.
    val local = Instant.fromEpochMilliseconds(modifiedMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val dosTime = ((local.hour shl 11) or (local.minute shl 5) or (local.second / 2)).toLong()
    val dosDate = (((local.year - 1980).coerceAtLeast(0) shl 9) or ((local.month.ordinal + 1) shl 5) or local.day).toLong()

    class Entry(val name: ByteArray, val crc: Long, val packed: Int, val size: Int, val offset: Int)
    val out = Out()
    val entries = ArrayList<Entry>()
    for (item in items) {
        val name = item.name.encodeToByteArray()
        val crc = crc32(item.bytes).toLong() and 0xFFFFFFFFL
        val packed = deflateRaw(item.bytes)
        entries += Entry(name, crc, packed.size, item.bytes.size, out.size)
        // Local header: version 20, UTF-8 name and sizes after the data (0x0808), deflated; then the data and its descriptor.
        out.put(0x04034b50, 4); out.put(20, 2); out.put(0x0808, 2); out.put(8, 2); out.put(dosTime, 2); out.put(dosDate, 2)
        out.put(0, 4); out.put(0, 4); out.put(0, 4); out.put(name.size.toLong(), 2); out.put(0, 2); out.put(name); out.put(packed)
        out.put(0x08074b50, 4); out.put(crc, 4); out.put(packed.size.toLong(), 4); out.put(item.bytes.size.toLong(), 4)
    }
    val centralStart = out.size
    for (e in entries) {
        out.put(0x02014b50, 4); out.put(20, 2); out.put(20, 2); out.put(0x0808, 2); out.put(8, 2); out.put(dosTime, 2); out.put(dosDate, 2)
        out.put(e.crc, 4); out.put(e.packed.toLong(), 4); out.put(e.size.toLong(), 4)
        out.put(e.name.size.toLong(), 2); out.put(0, 2); out.put(0, 2); out.put(0, 2); out.put(0, 2); out.put(0, 4); out.put(e.offset.toLong(), 4); out.put(e.name)
    }
    val centralSize = out.size - centralStart
    out.put(0x06054b50, 4); out.put(0, 2); out.put(0, 2); out.put(entries.size.toLong(), 2); out.put(entries.size.toLong(), 2)
    out.put(centralSize.toLong(), 4); out.put(centralStart.toLong(), 4); out.put(0, 2)
    return out.data.copyOf(out.size)
}
