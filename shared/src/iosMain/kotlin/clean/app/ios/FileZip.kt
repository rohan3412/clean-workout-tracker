@file:OptIn(ExperimentalForeignApi::class)

package clean.app.ios

import clean.core.inflateRaw
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

/**
 * A zip read from a file without holding it in memory: the central directory first, then one entry at a time on request, so a backup or the
 * animations' zip of a gigabyte is never loaded whole. Stored and deflated entries, and Zip64 (the animations' zip may pass 4 GiB).
 * Call [close] when done.
 */
class FileZip(path: String) {
    class Entry(val name: String, val method: Int, val packed: Long, val size: Long, val offset: Long) {
        val isDirectory: Boolean get() = name.endsWith("/")
    }

    private val file = fopen(path, "rb") ?: throw IllegalArgumentException("That file could not be opened")
    val entries: List<Entry>

    init {
        try {
            entries = readDirectory()
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    fun close() { fclose(file) }

    private fun read(at: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        if (length == 0) return out
        if (fseek(file, at, SEEK_SET) != 0) throw IllegalArgumentException("The zip is cut short")
        val got = out.usePinned { fread(it.addressOf(0), 1.toULong(), length.toULong(), file) }
        if (got.toLong() != length.toLong()) throw IllegalArgumentException("The zip is cut short")
        return out
    }

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, at: Int): Long = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)
    private fun u64(b: ByteArray, at: Int): Long = u32(b, at) or (u32(b, at + 4) shl 32)

    private fun readDirectory(): List<Entry> {
        fseek(file, 0, SEEK_END)
        val length = ftell(file)
        val tailSize = minOf(length, 22L + 65535L).toInt()
        val tailAt = length - tailSize
        val tail = read(tailAt, tailSize)
        var end = tailSize - 22
        while (end >= 0 && u32(tail, end) != 0x06054b50L) end--
        if (end < 0) throw IllegalArgumentException("That file is not a zip")
        var count = u16(tail, end + 10).toLong()
        var size = u32(tail, end + 12)
        var start = u32(tail, end + 16)
        if (count == 0xFFFFL || start == 0xFFFFFFFFL || size == 0xFFFFFFFFL) {
            // Zip64: the locator sits just before the end record and points to the larger one.
            val locatorAt = tailAt + end - 20
            if (locatorAt >= 0) {
                val locator = read(locatorAt, 20)
                if (u32(locator, 0) == 0x07064b50L) {
                    val record = read(u64(locator, 8), 56)
                    if (u32(record, 0) == 0x06064b50L) {
                        count = u64(record, 32); size = u64(record, 40); start = u64(record, 48)
                    }
                }
            }
        }
        val directory = read(start, size.toInt())
        val items = ArrayList<Entry>()
        var at = 0
        for (i in 0 until count) {
            if (at + 46 > directory.size || u32(directory, at) != 0x02014b50L) throw IllegalArgumentException("The zip's directory is damaged")
            val method = u16(directory, at + 10)
            var packed = u32(directory, at + 20)
            var original = u32(directory, at + 24)
            val nameLength = u16(directory, at + 28)
            val extraLength = u16(directory, at + 30)
            val commentLength = u16(directory, at + 32)
            var offset = u32(directory, at + 42)
            val name = directory.decodeToString(at + 46, at + 46 + nameLength)
            // The Zip64 extra field holds, in this order, only the values that were 0xFFFFFFFF.
            var extra = at + 46 + nameLength
            val extraEnd = extra + extraLength
            while (extra + 4 <= extraEnd) {
                val id = u16(directory, extra)
                val dataLength = u16(directory, extra + 2)
                if (id == 1) {
                    var p = extra + 4
                    if (original == 0xFFFFFFFFL) { original = u64(directory, p); p += 8 }
                    if (packed == 0xFFFFFFFFL) { packed = u64(directory, p); p += 8 }
                    if (offset == 0xFFFFFFFFL) { offset = u64(directory, p) }
                }
                extra += 4 + dataLength
            }
            at += 46 + nameLength + extraLength + commentLength
            items += Entry(name, method, packed, original, offset)
        }
        return items
    }

    /** The bytes of [entry]. */
    fun bytes(entry: Entry): ByteArray {
        val header = read(entry.offset, 30)
        if (u32(header, 0) != 0x04034b50L) throw IllegalArgumentException("The zip's directory is damaged")
        val start = entry.offset + 30 + u16(header, 26) + u16(header, 28)
        if (entry.packed > Int.MAX_VALUE || entry.size > Int.MAX_VALUE) throw IllegalArgumentException("A zip entry is too large to read")
        val packed = read(start, entry.packed.toInt())
        return when (entry.method) {
            0 -> packed
            8 -> inflateRaw(packed, 0, packed.size, entry.size.toInt())
            else -> throw IllegalArgumentException("A zip entry uses a compression this app does not read")
        }
    }
}
