package com.txao.authenticator

/** RFC 4648 base32 (the alphabet used by otpauth secrets). */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Decodes [text]; spaces, hyphens, `=` padding and letter case are ignored. Returns null if invalid. */
    fun decode(text: String): ByteArray? {
        val clean = text.filter { it != ' ' && it != '-' && it != '\n' && it != '\t' }
            .trimEnd('=').uppercase()
        if (clean.isEmpty()) return null
        // 1, 3 and 6 leftover characters can never occur in a valid encoding.
        if (clean.length % 8 in intArrayOf(1, 3, 6)) return null
        val out = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[index++] = (buffer shr bits).toByte()
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer shr bits) and 31])
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        while (sb.length % 8 != 0) sb.append('=')
        return sb.toString()
    }
}
