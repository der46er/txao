package com.txao.authenticator

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** STEAM is the 5-character, SHA-1/30 s variant used by Steam Guard. */
enum class OtpType { TOTP, HOTP, STEAM }

enum class OtpAlgorithm(val jca: String, val uriName: String) {
    SHA1("HmacSHA1", "SHA1"),
    SHA256("HmacSHA256", "SHA256"),
    SHA512("HmacSHA512", "SHA512");

    companion object {
        fun fromUri(name: String): OtpAlgorithm? = entries.firstOrNull { it.uriName.equals(name, true) || it.name.equals(name, true) }
    }
}

/** One-time password generation: HOTP (RFC 4226) and TOTP (RFC 6238). */
object Otp {
    /** HOTP value for [counter], as a zero-padded string of [digits] digits (6 to 8). */
    fun hotp(secret: ByteArray, counter: Long, digits: Int = 6, algorithm: OtpAlgorithm = OtpAlgorithm.SHA1): String {
        require(digits in 6..8) { "digits must be 6..8" }
        require(secret.isNotEmpty()) { "empty secret" }
        val mac = Mac.getInstance(algorithm.jca)
        mac.init(SecretKeySpec(secret, algorithm.jca))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        // Dynamic truncation: the low nibble of the last byte picks a 4-byte window.
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (binary % mod).toString().padStart(digits, '0')
    }

    /** Index of the time step containing [unixSeconds]. */
    fun timeStep(unixSeconds: Long, period: Int = 30): Long = Math.floorDiv(unixSeconds, period.toLong())

    fun totp(secret: ByteArray, unixSeconds: Long, period: Int = 30, digits: Int = 6, algorithm: OtpAlgorithm = OtpAlgorithm.SHA1): String =
        hotp(secret, timeStep(unixSeconds, period), digits, algorithm)

    /** Whole seconds until the current TOTP code rolls over (1..period). */
    fun secondsRemaining(unixMillis: Long, period: Int): Int {
        val p = period * 1000L
        return (((p - Math.floorMod(unixMillis, p)) + 999) / 1000).toInt()
    }

    private const val STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"

    /** Steam Guard code: the usual HOTP truncation, written as 5 characters of a 26-symbol alphabet. */
    fun steam(secret: ByteArray, unixSeconds: Long): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timeStep(unixSeconds, 30)).array())
        val offset = hash[hash.size - 1].toInt() and 0x0F
        var v = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val sb = StringBuilder()
        repeat(5) {
            sb.append(STEAM_ALPHABET[v % STEAM_ALPHABET.length])
            v /= STEAM_ALPHABET.length
        }
        return sb.toString()
    }

    /** Groups a code for readability: "123456" -> "123 456", "12345678" -> "1234 5678". */
    fun format(code: String): String {
        val first = code.length / 2
        return if (code.length < 6) code else code.substring(0, first) + " " + code.substring(first)
    }
}
