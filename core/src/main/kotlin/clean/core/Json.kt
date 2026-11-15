package clean.core

/**
 * A small JSON reader for files from other apps (openGym's backup) and for checking Clean's own JSON export:
 * objects come back as maps, arrays as lists, numbers as doubles, plus strings, booleans and null.
 */
internal object Json {
    fun parse(text: String): Any? {
        val reader = Reader(text.removePrefix("﻿"))
        val value = reader.value()
        reader.skipSpace()
        if (!reader.done()) throw CsvFormatException("Not valid JSON: unexpected text after the end")
        return value
    }

    /** [s] as a JSON string literal, with the escapes the format needs. */
    fun quote(s: String): String {
        val out = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> out.append("\\\"")
            c == '\\' -> out.append("\\\\")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c < ' ' -> out.append("\\u%04x".format(c.code))
            else -> out.append(c)
        }
        return out.append('"').toString()
    }

    private class Reader(val s: String) {
        var i = 0

        fun done() = i >= s.length

        fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun fail(what: String): Nothing = throw CsvFormatException("Not valid JSON: $what at character $i")

        fun value(): Any? {
            skipSpace()
            if (done()) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else fail("unexpected '$c'")
            }
        }

        fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("unexpected text")
            i += word.length
            return v
        }

        fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: fail("bad number")
        }

        fun string(): String {
            i++
            val out = StringBuilder()
            while (true) {
                if (done()) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (done()) fail("unterminated string")
                        when (val e = s[i++]) {
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'u' -> {
                                val code = s.substring(i, minOf(i + 4, s.length)).toIntOrNull(16) ?: fail("bad escape")
                                out.append(code.toChar())
                                i += 4
                            }
                            else -> out.append(e)
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        fun array(): List<Any?> {
            i++
            val out = mutableListOf<Any?>()
            skipSpace()
            if (s.getOrNull(i) == ']') { i++; return out }
            while (true) {
                out += value()
                skipSpace()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    ']' -> return out
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            skipSpace()
            if (s.getOrNull(i) == '}') { i++; return out }
            while (true) {
                skipSpace()
                if (s.getOrNull(i) != '"') fail("expected a name")
                val name = string()
                skipSpace()
                if (s.getOrNull(i++) != ':') fail("expected ':'")
                out[name] = value()
                skipSpace()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    '}' -> return out
                    else -> fail("expected ',' or '}'")
                }
            }
        }
    }
}
