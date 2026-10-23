package com.txao.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption shared by every TXAO app (same scheme as Txao Notes 1.0).
 *
 * One 256-bit sync key, shown as "TXAO-XXXX-XXXX-...", works in all apps. Per app, two independent
 * values are derived with HMAC-SHA256(key, "txao-<app>/auth/v1" | "txao-<app>/enc/v1"): a login
 * token (the server stores only its SHA-256) and an AES-256-GCM key that never leaves the device.
 * Every app therefore gets its own server account and its own cipher key from the same user key,
 * and the server can read nothing. For app = "notes" the labels are byte-identical to Txao Notes.
 */
object TxaoCrypto {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val KEY_BYTES = 32

    class Keys(val authToken: String, val enc: SecretKeySpec)

    fun generateKey(): String = formatKey(ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) })

    fun formatKey(bytes: ByteArray): String = "TXAO-" + base32(bytes).chunked(4).joinToString("-")

    /** Accepts spaces, dashes and lower case. Returns null if it isn't a valid key. */
    fun parseKey(input: String): ByteArray? {
        var s = input.trim().uppercase()
        if (s.startsWith("TXAO")) s = s.removePrefix("TXAO")
        s = s.filter { it in ALPHABET }
        if (s.length != 52) return null
        val out = ByteArray(KEY_BYTES)
        var buffer = 0
        var bits = 0
        var idx = 0
        for (c in s) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(c)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                if (idx < KEY_BYTES) out[idx++] = ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return if (idx == KEY_BYTES) out else null
    }

    private fun base32(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun hmac(key: ByteArray, label: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(label.toByteArray())
    }

    /** [app] is the lowercase app id: notes, calculator, gallery, calendar, authenticator, sms, phone, music. */
    fun derive(key: ByteArray, app: String): Keys {
        val auth = hmac(key, "txao-$app/auth/v1").joinToString("") { "%02x".format(it) }
        val enc = SecretKeySpec(hmac(key, "txao-$app/enc/v1"), "AES")
        return Keys(auth, enc)
    }

    /** The account id the server derives from the token (useful for tests and diagnostics only). */
    fun accountId(keys: Keys): String =
        MessageDigest.getInstance("SHA-256").digest(keys.authToken.toByteArray()).joinToString("") { "%02x".format(it) }

    /** AES-256-GCM. Output = 12-byte random nonce + ciphertext+tag. [aad] ties the data to its id. */
    fun encrypt(keys: Keys, plain: ByteArray, aad: String): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keys.enc, GCMParameterSpec(128, nonce))
        c.updateAAD(aad.toByteArray())
        return nonce + c.doFinal(plain)
    }

    fun decrypt(keys: Keys, data: ByteArray, aad: String): ByteArray {
        require(data.size > 12 + 16) { "too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keys.enc, GCMParameterSpec(128, data.copyOfRange(0, 12)))
        c.updateAAD(aad.toByteArray())
        return c.doFinal(data, 12, data.size - 12)
    }
}
