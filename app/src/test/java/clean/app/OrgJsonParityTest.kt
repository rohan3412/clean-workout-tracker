package clean.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `clean.app.data.JSONObject` / `JSONArray` (common code, kotlinx.serialization underneath) must write the same bytes as Android's `org.json`,
 * which wrote everything the phone already stores. Robolectric runs the real Android implementation, so this compares the two directly:
 * escaping, number formatting, key order, indentation, and what each reads back from text the old code wrote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OrgJsonParityTest {
    private val NULL = Any()

    private fun old(v: Any?): Any? = when (v) {
        null, NULL -> org.json.JSONObject.NULL
        is Map<*, *> -> org.json.JSONObject().also { o -> for ((k, x) in v) o.put(k as String, old(x)) }
        is List<*> -> org.json.JSONArray().also { a -> for (x in v) a.put(old(x)) }
        else -> v
    }

    private fun new(v: Any?): Any? = when (v) {
        null, NULL -> clean.app.data.JSONObject.NULL
        is Map<*, *> -> clean.app.data.JSONObject().also { o -> for ((k, x) in v) o.put(k as String, new(x)) }
        is List<*> -> clean.app.data.JSONArray().also { a -> for (x in v) a.put(new(x)) }
        else -> v
    }

    private val strings = listOf(
        "", "plain", "a/b", "back\\slash", "quote\"s", "tab\tnew\nline\rret\u000cff\bbs", "\u0000\u0001\u001f\u007f", "café     💪", "</script>", "  trailing  ",
    )
    private val numbers: List<Any> = listOf(
        0, 1, -1, 5.0, -0.0, 2.5, 0.1 + 0.2, 100.0, 1e20, 1e-5, 123456789012345678L, Long.MAX_VALUE, Long.MIN_VALUE, Int.MIN_VALUE, 2.0f, 0.5f, 1234567.125, 1e15, 9.007199254740993E15,
    )

    private fun cases(): List<Any?> = buildList {
        add(mapOf("empty" to emptyMap<String, Any?>(), "emptyList" to emptyList<Any?>()))
        add(mapOf("strings" to strings, "keys" to strings.associateWith { 1 }))
        add(mapOf("numbers" to numbers, "n" to numbers.associate { "k$it" to it }))
        add(mapOf("mixed" to listOf(true, false, NULL, "x", 3, 2.5, listOf(1, 2), mapOf("a" to listOf<Any?>()))))
        add(
            mapOf(
                "name" to "Push", "start" to 1_763_600_000_000L, "lastDone" to 1_763_500_000_000L,
                "blocks" to listOf(
                    mapOf(
                        "exercise" to "Bench Press (Barbell)", "targets" to listOf(listOf(100.0, 5), NULL, listOf(NULL, 8)), "paired" to true, "id" to 1_763_600_000_000_123L,
                        "items" to listOf(mapOf("type" to "NORMAL", "weight" to "100", "reps" to "5", "done" to true, "rpe" to "8", "rest" to 90, "ticked" to 0L), mapOf("note" to "slow / controlled")),
                    ),
                ),
            ),
        )
    }

    @Test fun `objects and arrays are written exactly as org json wrote them`() {
        for (c in cases()) {
            val o = old(c); val n = new(c)
            assertEquals(o.toString(), n.toString())
            if (o is org.json.JSONObject) assertEquals(o.toString(2), (n as clean.app.data.JSONObject).toString(2))
        }
        for (s in strings) assertEquals(org.json.JSONArray().put(s).toString(), clean.app.data.JSONArray().put(s).toString())
        for (x in numbers) assertEquals(org.json.JSONArray().put(x).toString(), clean.app.data.JSONArray().put(x).toString())
        assertEquals(org.json.JSONObject().toString(2), clean.app.data.JSONObject().toString(2))
        assertEquals(org.json.JSONArray().toString(2), clean.app.data.JSONArray().toString(2))
    }

    @Test fun `text the old code wrote reads back and writes back the same`() {
        val texts = listOf(
            """{"a":1,"b":2.50,"c":"x\/y","d":[null,true,1e3,-0,12345678901,3.0],"e":{},"f":[],"g":"A\n"}""",
            """[{"exercise":"Squat","sets":["N","W"],"targets":[[100,5],null,[null,8]]}]""",
            """{"weight":"","rest":0,"note":"tab\there"}""",
            "{\n  \"x\": [\n    1,\n    2\n  ]\n}",
        )
        for (t in texts) {
            if (t.trimStart().startsWith("{")) {
                val o = org.json.JSONObject(t); val n = clean.app.data.JSONObject(t)
                assertEquals(o.toString(), n.toString())
                assertEquals(o.toString(2), n.toString(2))
            } else {
                val o = org.json.JSONArray(t); val n = clean.app.data.JSONArray(t)
                assertEquals(o.toString(), n.toString())
            }
        }
    }

    @Test fun `getters agree`() {
        val t = """{"s":"v","i":5,"l":1763600000000,"d":2.5,"w":3.0,"b":true,"z":null,"a":[1,2.5,"x",null],"o":{"k":1}}"""
        val o = org.json.JSONObject(t); val n = clean.app.data.JSONObject(t)
        assertEquals(o.getString("s"), n.getString("s"))
        assertEquals(o.getString("i"), n.getString("i"))
        assertEquals(o.getString("w"), n.getString("w"))
        assertEquals(o.getInt("i"), n.getInt("i"))
        assertEquals(o.getInt("w"), n.getInt("w"))
        assertEquals(o.getLong("l"), n.getLong("l"))
        assertEquals(o.getDouble("d"), n.getDouble("d"), 0.0)
        assertEquals(o.getDouble("i"), n.getDouble("i"), 0.0)
        assertEquals(o.getBoolean("b"), n.getBoolean("b"))
        assertEquals(o.optString("missing"), n.optString("missing"))
        assertEquals(o.optInt("missing"), n.optInt("missing"))
        assertEquals(o.optLong("missing"), n.optLong("missing"))
        assertEquals(o.optBoolean("missing", true), n.optBoolean("missing", true))
        assertEquals(o.optBoolean("missing"), n.optBoolean("missing"))
        assertEquals(o.has("z"), n.has("z"))
        assertEquals(o.isNull("z"), n.isNull("z"))
        assertEquals(o.has("nope"), n.has("nope"))
        assertEquals(o.length(), n.length())
        assertEquals(o.getJSONObject("o").getInt("k"), n.getJSONObject("o").getInt("k"))
        val oa = o.getJSONArray("a"); val na = n.getJSONArray("a")
        assertEquals(oa.length(), na.length())
        assertEquals(oa.getInt(0), na.getInt(0))
        assertEquals(oa.getDouble(1), na.getDouble(1), 0.0)
        assertEquals(oa.getString(2), na.getString(2))
        assertEquals(oa.isNull(3), na.isNull(3))
        assertEquals(o.optJSONArray("s") == null, n.optJSONArray("s") == null)
        assertEquals(o.keys().asSequence().toList(), n.keys().asSequence().toList())
    }
}
