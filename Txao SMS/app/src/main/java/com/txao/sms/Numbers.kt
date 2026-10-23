package com.txao.sms

/** Phone-number helpers. Pure Kotlin so they can be unit tested. */
object Numbers {
    /** Alphanumeric sender ids ("BANK") and short codes are not real phone numbers. */
    fun isAlpha(address: String) = address.any { it.isLetter() }

    /** Strips formatting, keeping a leading + and the digits. */
    fun clean(address: String): String {
        val t = address.trim()
        if (isAlpha(t)) return t
        val digits = t.filter { it.isDigit() }
        return if (t.startsWith("+")) "+$digits" else digits
    }

    /** Two addresses are the same person if their last 9 digits match (ignores country prefixes). */
    fun same(a: String, b: String): Boolean {
        if (isAlpha(a) || isAlpha(b)) return a.equals(b, ignoreCase = true)
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        val n = minOf(9, da.length, db.length)
        return da.takeLast(n) == db.takeLast(n)
    }

    /** Splits "a, b;c" into cleaned, non-empty recipients. */
    fun parseRecipients(input: String): List<String> =
        input.split(',', ';', '\n').map { clean(it) }.filter { it.isNotEmpty() }.distinct()
}

/** How many SMS segments a text costs. */
object SmsText {
    enum class Encoding { GSM, UNICODE }
    data class Info(val encoding: Encoding, val length: Int, val parts: Int, val remaining: Int)

    private const val GSM_BASIC = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val GSM_EXT = "^{}\\[~]|€\u000c"

    fun info(text: String): Info {
        var units = 0
        var gsm = true
        for (c in text) {
            when {
                GSM_BASIC.indexOf(c) >= 0 -> units += 1
                GSM_EXT.indexOf(c) >= 0 -> units += 2
                else -> { gsm = false; break }
            }
        }
        if (gsm) {
            val (single, multi) = 160 to 153
            return build(Encoding.GSM, units, single, multi)
        }
        val len = text.length // UTF-16 code units, which is what the radio counts
        return build(Encoding.UNICODE, len, 70, 67)
    }

    private fun build(enc: Encoding, len: Int, single: Int, multi: Int): Info {
        if (len <= single) return Info(enc, len, if (len == 0) 0 else 1, single - len)
        val parts = (len + multi - 1) / multi
        return Info(enc, len, parts, parts * multi - len)
    }
}
