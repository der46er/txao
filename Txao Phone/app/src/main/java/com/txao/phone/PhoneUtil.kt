package com.txao.phone

/** Pure helpers for number cleanup and dial-pad search; no Android dependencies so they unit-test on the JVM. */
object PhoneUtil {
    private const val T9 = "22233344455566677778889999"

    /** Keeps digits, `*`, `#` and a leading `+`. */
    fun normalize(raw: String): String {
        val sb = StringBuilder()
        for ((i, c) in raw.trim().withIndex()) {
            if (c.isDigit() || c == '*' || c == '#' || (c == '+' && i == 0)) sb.append(c)
        }
        return sb.toString()
    }

    /** Dial-pad digits for a name: letters become their key, everything else is dropped. */
    fun t9(name: String): String = buildString {
        for (c in name.lowercase()) {
            if (c in 'a'..'z') append(T9[c - 'a']) else if (c.isDigit()) append(c)
        }
    }

    /** True when [query] (dial-pad digits) appears in the number, or in the name typed on a keypad. */
    fun matches(query: String, name: String, number: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        if (normalize(number).contains(q)) return true
        val words = name.split(' ', '-').filter { it.isNotEmpty() }
        return t9(name).contains(q) || words.any { t9(it).startsWith(q) }
    }

    /** Light grouping for display: `+431234567` stays as typed, local 10-digit numbers get spaced. */
    fun pretty(number: String): String {
        val n = normalize(number)
        if (n.startsWith("+") || n.length < 7 || n.any { !it.isDigit() }) return n.ifEmpty { number }
        val head = n.take(n.length - 7)
        val rest = n.takeLast(7)
        return listOf(head, rest.take(3), rest.drop(3)).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
