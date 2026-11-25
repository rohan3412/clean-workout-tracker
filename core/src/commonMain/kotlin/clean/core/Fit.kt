package clean.core

import kotlinx.datetime.offsetAt
import clean.core.time.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/*
 * One workout as a FIT activity file, the format Strava's upload page takes (docs/other-formats.md). Written from the public
 * description of the format: a 14-byte header, definition and data messages, a CRC-16 at the end, all little-endian.
 * Messages: file_id, event (timer start), event (timer stop), lap, session (training, strength training), activity.
 */

/** CRC-16/ARC as FIT uses it (polynomial 0xA001 reflected, start 0). */
internal fun fitCrc(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Int {
    var crc = 0
    for (i in from until to) {
        crc = crc xor (bytes[i].toInt() and 0xFF)
        repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
    }
    return crc
}

private const val FIT_EPOCH_UNIX = 631065600L // 1989-12-31 00:00:00 UTC in Unix seconds

private const val ENUM = 0x00
private const val UINT16 = 0x84
private const val UINT32 = 0x86

/** The bytes written so far (java's ByteArrayOutputStream, which common Kotlin does not have). */
private class ByteSink {
    private var bytes = ByteArray(256)
    private var size = 0
    fun write(b: Int) {
        if (size == bytes.size) bytes = bytes.copyOf(size * 2)
        bytes[size++] = b.toByte()
    }
    fun write(more: ByteArray) { for (b in more) write(b.toInt()) }
    fun toByteArray(): ByteArray = bytes.copyOf(size)
}

/** A field of a message: its number, its size in bytes and its FIT base type. */
private class Field(val number: Int, val size: Int, val type: Int, val value: Long)

private fun ByteSink.le(value: Long, bytes: Int) {
    for (i in 0 until bytes) write(((value ushr (8 * i)) and 0xFF).toInt())
}

/** Writes a definition message and then one data message for [fields] under local type [local]. */
private fun ByteSink.message(local: Int, global: Int, fields: List<Field>) {
    write(0x40 or local) // definition header
    write(0) // reserved
    write(0) // little-endian
    le(global.toLong(), 2)
    write(fields.size)
    for (f in fields) { write(f.number); write(f.size); write(f.type) }
    write(local) // data header
    for (f in fields) le(f.value, f.size)
}

private fun u32(number: Int, value: Long) = Field(number, 4, UINT32, value)
private fun u16(number: Int, value: Long) = Field(number, 2, UINT16, value)
private fun e8(number: Int, value: Int) = Field(number, 1, ENUM, value.toLong())

/** [workout] as the bytes of a `.fit` file. Its stored start is local time, placed in UTC by [zone]. */
fun exportFit(workout: Workout, zone: TimeZone): ByteArray {
    val local = parseStrongDate(workout.date) ?: throw CsvFormatException("The workout has no readable start time")
    val instant = local.toInstant(zone)
    val start = instant.epochSeconds - FIT_EPOCH_UNIX
    val length = workout.durationSeconds
    val end = start + length
    val millis = length * 1000
    val sport = 10 // training
    val strength = 20 // strength training

    val data = ByteSink()
    // file_id: type 4 is an activity; manufacturer 255 is the one FIT sets aside for development.
    data.message(0, 0, listOf(e8(0, 4), u16(1, 255), u16(2, 0), u32(4, start)))
    // event: the timer starts, then stops.
    data.message(1, 21, listOf(u32(253, start), e8(0, 0), e8(1, 0), Field(4, 1, 0x02, 0)))
    data.message(1, 21, listOf(u32(253, end), e8(0, 0), e8(1, 4), Field(4, 1, 0x02, 0)))
    // lap: the whole workout as one lap.
    data.message(2, 19, listOf(u32(253, end), u32(2, start), u32(7, millis), u32(8, millis), e8(0, 9), e8(1, 1), e8(25, sport), e8(39, strength)))
    // session: sport training, sub-sport strength training.
    data.message(3, 18, listOf(u32(253, end), u32(2, start), u32(7, millis), u32(8, millis), e8(0, 8), e8(1, 1), e8(5, sport), e8(6, strength), u16(25, 0), u16(26, 1)))
    // activity: one session, with the local clock time beside the UTC one.
    data.message(4, 34, listOf(u32(253, end), u32(0, millis), u16(1, 1), e8(2, 0), e8(3, 26), e8(4, 1), u32(5, end + zone.offsetAt(instant).totalSeconds)))
    val body = data.toByteArray()

    val out = ByteSink()
    out.write(14) // header size
    out.write(0x20) // protocol version 2.0
    out.le(2132, 2) // profile version 21.32
    out.le(body.size.toLong(), 4)
    out.write(".FIT".encodeToByteArray())
    val head = out.toByteArray()
    out.le(fitCrc(head).toLong(), 2)
    out.write(body)
    val all = out.toByteArray()
    out.le(fitCrc(all).toLong(), 2)
    return out.toByteArray()
}
