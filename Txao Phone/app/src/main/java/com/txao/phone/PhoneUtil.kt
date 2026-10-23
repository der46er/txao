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

    /** Last digits of a number: equal keys mean "same number" regardless of country prefix or spacing. */
    fun key(number: String): String = normalize(number).filter { it.isDigit() }.takeLast(9)

    fun sameNumber(a: String, b: String): Boolean {
        val ka = key(a)
        return ka.isNotEmpty() && ka == key(b)
    }

    /** Alphabet section letter for the contacts list; everything non-alphabetic goes under `#`. */
    fun section(name: String): Char = name.trim().firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

    /** Short label such as "Mobile" for call-log rows is not stored; this gives a relative day bucket. */
    fun dayLabel(now: Long, then: Long, dayMs: Long = 86_400_000L): String {
        val d = (startOfDay(now) - startOfDay(then)) / dayMs
        return when {
            d <= 0L -> "Today"
            d == 1L -> "Yesterday"
            else -> "$d days ago"
        }
    }

    private fun startOfDay(t: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
