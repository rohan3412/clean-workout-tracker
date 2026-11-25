package clean.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray as KArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject as KObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/*
 * The JSON the app keeps in its database and writes to files (the workout in progress, a template's blocks, the rename log, the Iso-Lateral
 * Row fix's undo, a template file) was written by Android's `org.json` until stage 2b. The text must stay byte for byte what that wrote,
 * because the phone holds such text already and reads it back, and a backup or a templates file must not change.
 *
 * So this is `JSONObject` and `JSONArray` with the methods the app used, in common code: kotlinx.serialization-json reads the text into
 * a tree of its own (`JsonElement`), and the writer is Android's: keys in the order they were put, `/` written as `\/`, control characters
 * as `\u00xx` in lower case, a whole number written without a decimal point (`5.0` is `5`), `-0` for negative zero, other doubles as
 * `Double.toString`. `OrgJsonParityTest` (app/src/test) checks these against the real `org.json` under Robolectric.
 */

class JSONException(message: String) : Exception(message)

private object Null { override fun toString() = "null" }

class JSONObject() {
    private val map = LinkedHashMap<String, Any>()

    constructor(text: String) : this() {
        val tree = parse(text)
        if (tree !is KObject) throw JSONException("Value of type ${tree::class.simpleName} cannot be converted to JSONObject")
        for ((k, v) in tree) map[k] = valueOf(v)
    }

    /** Sets [name] to [value] (a string, number, boolean, [JSONObject], [JSONArray] or [NULL]); null removes the name. */
    fun put(name: String, value: Any?): JSONObject {
        if (value == null) map.remove(name) else map[name] = checked(value)
        return this
    }

    fun has(name: String) = map.containsKey(name)
    fun isNull(name: String) = map[name].let { it == null || it === Null }
    fun length() = map.size
    fun keys(): Iterator<String> = map.keys.toList().iterator()
    fun remove(name: String): Any? = map.remove(name)

    fun getString(name: String) = stringOf(need(name))
    fun getLong(name: String) = longOf(need(name)) ?: throw JSONException("Value for $name cannot be converted to long")
    fun getInt(name: String) = intOf(need(name)) ?: throw JSONException("Value for $name cannot be converted to int")
    fun getDouble(name: String) = doubleOf(need(name)) ?: throw JSONException("Value for $name cannot be converted to double")
    fun getBoolean(name: String) = booleanOf(need(name)) ?: throw JSONException("Value for $name cannot be converted to boolean")
    fun getJSONArray(name: String) = need(name) as? JSONArray ?: throw JSONException("Value for $name cannot be converted to JSONArray")
    fun getJSONObject(name: String) = need(name) as? JSONObject ?: throw JSONException("Value for $name cannot be converted to JSONObject")

    fun optString(name: String, fallback: String = "") = map[name]?.let(::stringOf) ?: fallback
    fun optInt(name: String, fallback: Int = 0) = map[name]?.let(::intOf) ?: fallback
    fun optLong(name: String, fallback: Long = 0) = map[name]?.let(::longOf) ?: fallback
    fun optBoolean(name: String, fallback: Boolean = false) = map[name]?.let(::booleanOf) ?: fallback
    fun optJSONArray(name: String) = map[name] as? JSONArray

    private fun need(name: String): Any = map[name] ?: throw JSONException("No value for $name")

    override fun toString(): String = StringBuilder().also { write(it, 0, 0) }.toString()

    /** Pretty-printed, [indent] spaces a level, as `JSONObject.toString(int)` wrote it. */
    fun toString(indent: Int): String = StringBuilder().also { write(it, indent, 0) }.toString()

    internal fun write(out: StringBuilder, indent: Int, depth: Int) {
        out.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) out.append(',')
            first = false
            newline(out, indent, depth + 1)
            writeString(out, k); out.append(if (indent == 0) ":" else ": "); writeValue(out, v, indent, depth + 1)
        }
        if (!first) newline(out, indent, depth)
        out.append('}')
    }

    companion object {
        /** `JSONObject.NULL`: written as `null`. */
        val NULL: Any = Null
    }
}

class JSONArray() {
    private val list = ArrayList<Any>()

    constructor(text: String) : this() {
        val tree = parse(text)
        if (tree !is KArray) throw JSONException("Value of type ${tree::class.simpleName} cannot be converted to JSONArray")
        for (v in tree) list.add(valueOf(v))
    }

    /** The items of [items]: strings, numbers, booleans, [JSONObject]s, [JSONArray]s or [JSONObject.NULL]; null becomes NULL. */
    constructor(items: Collection<*>) : this() { for (i in items) list.add(if (i == null) Null else checked(i)) }

    fun put(value: Any?): JSONArray { list.add(if (value == null) Null else checked(value)); return this }

    fun length() = list.size
    fun isNull(index: Int) = list.getOrNull(index).let { it == null || it === Null }

    fun getString(index: Int) = stringOf(need(index))
    fun getLong(index: Int) = longOf(need(index)) ?: throw JSONException("Value at $index cannot be converted to long")
    fun getInt(index: Int) = intOf(need(index)) ?: throw JSONException("Value at $index cannot be converted to int")
    fun getDouble(index: Int) = doubleOf(need(index)) ?: throw JSONException("Value at $index cannot be converted to double")
    fun getJSONObject(index: Int) = need(index) as? JSONObject ?: throw JSONException("Value at $index cannot be converted to JSONObject")
    fun getJSONArray(index: Int) = need(index) as? JSONArray ?: throw JSONException("Value at $index cannot be converted to JSONArray")
    fun optJSONArray(index: Int) = list.getOrNull(index) as? JSONArray

    private fun need(index: Int): Any = list.getOrNull(index) ?: throw JSONException("Index $index out of range [0..${list.size})")

    override fun toString(): String = StringBuilder().also { write(it, 0, 0) }.toString()

    fun toString(indent: Int): String = StringBuilder().also { write(it, indent, 0) }.toString()

    internal fun write(out: StringBuilder, indent: Int, depth: Int) {
        out.append('[')
        for ((i, v) in list.withIndex()) {
            if (i > 0) out.append(',')
            newline(out, indent, depth + 1)
            writeValue(out, v, indent, depth + 1)
        }
        if (list.isNotEmpty()) newline(out, indent, depth)
        out.append(']')
    }
}

private fun parse(text: String): JsonElement = try {
    Json.parseToJsonElement(text)
} catch (e: Exception) {
    throw JSONException(e.message ?: "bad JSON")
}

/** What Android's tokener makes of a literal: a whole number without a decimal point is an Int or a Long, anything else numeric a Double. */
private fun valueOf(e: JsonElement): Any = when (e) {
    is JsonNull -> Null
    is KObject -> JSONObject().also { o -> for ((k, v) in e) o.put(k, valueOf(v)) }
    is KArray -> JSONArray().also { a -> for (v in e) a.put(valueOf(v)) }
    is JsonPrimitive -> when {
        e.isString -> e.content
        e.booleanOrNull != null -> e.booleanOrNull!!
        '.' !in e.content && e.content.toLongOrNull() != null -> e.content.toLong().let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
        else -> e.content.toDouble()
    }
}

private fun checked(value: Any): Any {
    if (value is Number) {
        val d = value.toDouble()
        if (d.isNaN() || d.isInfinite()) throw JSONException("Forbidden numeric value: $d")
    }
    return value
}

private fun stringOf(v: Any): String = v.toString()
private fun longOf(v: Any): Long? = when (v) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
    else -> null
}
private fun intOf(v: Any): Int? = when (v) {
    is Number -> v.toInt()
    is String -> v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt()
    else -> null
}
private fun doubleOf(v: Any): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}
private fun booleanOf(v: Any): Boolean? = when (v) {
    is Boolean -> v
    is String -> if (v.equals("true", ignoreCase = true)) true else if (v.equals("false", ignoreCase = true)) false else null
    else -> null
}

private fun newline(out: StringBuilder, indent: Int, depth: Int) {
    if (indent == 0) return
    out.append('\n')
    repeat(indent * depth) { out.append(' ') }
}

private fun writeValue(out: StringBuilder, v: Any, indent: Int = 0, depth: Int = 0) {
    when (v) {
        is JSONObject -> v.write(out, indent, depth)
        is JSONArray -> v.write(out, indent, depth)
        is String -> writeString(out, v)
        is Number -> out.append(numberToString(v))
        else -> out.append(v.toString()) // Boolean and NULL
    }
}

/** Android's `JSON.numberToString`: whole numbers without a decimal point, negative zero as `-0`. */
internal fun numberToString(n: Number): String {
    val d = n.toDouble()
    if ((n is Double || n is Float) && d == 0.0 && 1.0 / d < 0) return "-0"
    val l = n.toLong()
    if (d == l.toDouble()) return l.toString()
    return n.toString()
}

private const val HEX = "0123456789abcdef"

private fun writeString(out: StringBuilder, s: String) {
    out.append('"')
    for (c in s) when (c) {
        '"', '\\', '/' -> out.append('\\').append(c)
        '\t' -> out.append("\\t")
        '\b' -> out.append("\\b")
        '\n' -> out.append("\\n")
        '\r' -> out.append("\\r")
        '\u000c' -> out.append("\\f")
        else -> if (c.code <= 0x1F) out.append("\\u00").append(HEX[c.code shr 4]).append(HEX[c.code and 15]) else out.append(c)
    }
    out.append('"')
}
