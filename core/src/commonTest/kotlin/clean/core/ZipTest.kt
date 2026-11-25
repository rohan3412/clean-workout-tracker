package clean.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Zip must read what the JVM (and so Android) writes and write what they read: this runs on the JVM and, in CI, on iOS over zlib. */
class ZipTest {
    private fun hello(): ByteArray = buildString { for (i in 0 until 40) append("hello clean ").append(i % 3).append('\n') }.encodeToByteArray()
    private fun ramp(): ByteArray = ByteArray(256) { (it * 7).toByte() }

    /** Written by java.util.zip.ZipOutputStream: deflated entries with data descriptors, an empty one and a UTF-8 name. */
    private val javaZip = "504b03041400080808006a1d6f570000000000000000000000000900000068656c6c6f2e747874cb48cdc9c95748ce494dcc5330e0ca40e219a2f08c5078a32a47aa4a00504b07083c12d3d01d00000030020000504b03041400080808006a1d6f5700000000000000000000000009000000656d7074792e7478740300504b0708000000000200000000000000504b03041400080808006a1d6f570000000000000000000000000d0000006469722f7ac3a9726f2e62696e010001fffe00070e151c232a31383f464d545b626970777e858c939aa1a8afb6bdc4cbd2d9e0e7eef5fc030a11181f262d343b424950575e656c737a81888f969da4abb2b9c0c7ced5dce3eaf1f8ff060d141b222930373e454c535a61686f767d848b9299a0a7aeb5bcc3cad1d8dfe6edf4fb020910171e252c333a41484f565d646b727980878e959ca3aab1b8bfc6cdd4dbe2e9f0f7fe050c131a21282f363d444b525960676e757c838a91989fa6adb4bbc2c9d0d7dee5ecf3fa01080f161d242b323940474e555c636a71787f868d949ba2a9b0b7bec5ccd3dae1e8eff6fd040b121920272e353c434a51585f666d747b828990979ea5acb3bac1c8cfd6dde4ebf2f9504b0708a3075c1a0501000000010000504b010214001400080808006a1d6f573c12d3d01d0000003002000009000000000000000000000000000000000068656c6c6f2e747874504b010214001400080808006a1d6f57000000000200000000000000090000000000000000000000000054000000656d7074792e747874504b010214001400080808006a1d6f57a3075c1a05010000000100000d000000000000000000000000008d0000006469722f7ac3a9726f2e62696e504b05060000000003000300a9000000cd0100000000"

    private fun bytesOf(hex: String) = ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    @Test fun readsAZipTheJvmWrote() {
        val items = readZipItems(bytesOf(javaZip))
        assertEquals(listOf("hello.txt", "empty.txt", "dir/zéro.bin"), items.map { it.name })
        assertContentEquals(hello(), items[0].bytes)
        assertEquals(0, items[1].bytes.size)
        assertContentEquals(ramp(), items[2].bytes)
    }

    @Test fun readsOnlyWhatIsWanted() {
        val items = readZipItems(bytesOf(javaZip)) { it.endsWith(".txt") }
        assertEquals(listOf("hello.txt", "empty.txt"), items.map { it.name })
    }

    @Test fun writtenThenRead() {
        val big = ByteArray(300_000) { ((it * 31) xor (it shr 7)).toByte() }
        val items = listOf(ZipItem("a.txt", hello()), ZipItem("empty", ByteArray(0)), ZipItem("f/ü.bin", big), ZipItem("r.bin", ramp()))
        val back = readZipItems(writeZipItems(items, 1_700_000_000_000L))
        assertEquals(items.map { it.name }, back.map { it.name })
        for (i in items.indices) assertContentEquals(items[i].bytes, back[i].bytes)
    }

    @Test fun measurementsZipRoundTrips() {
        val items = listOf(ZipItem("weight.csv", "Date,Weight\n2026-01-01,80.5\n".encodeToByteArray()))
        assertContentEquals(items[0].bytes, readZipItems(writeZipItems(items, 0L))[0].bytes)
    }
}
