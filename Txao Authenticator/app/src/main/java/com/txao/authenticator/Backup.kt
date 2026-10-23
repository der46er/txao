package com.txao.authenticator

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected backup file: PBKDF2-HMAC-SHA256 stretches the password into an AES-256-GCM key.
 * The file is JSON so it is easy to inspect; only the `data` field is secret.
 */
object Backup {
    const val FORMAT = "txao-authenticator-backup"
    private const val ITERATIONS = 600_000
    private val b64 = Base64.getEncoder()

    class WrongPasswordOrCorrupt : Exception()

    fun encrypt(accounts: List<Account>, password: CharArray, iterations: Int = ITERATIONS): String {
        val rng = SecureRandom()
        val salt = ByteArray(16).also(rng::nextBytes)
        val iv = ByteArray(12).also(rng::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(FORMAT.toByteArray())
        val data = cipher.doFinal(Account.listToJson(accounts).toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT).put("version", 1)
            .put("kdf", "PBKDF2-HMAC-SHA256").put("iterations", iterations)
            .put("salt", b64.encodeToString(salt)).put("iv", b64.encodeToString(iv))
            .put("data", b64.encodeToString(data))
            .toString(2)
    }

    fun isBackup(text: String): Boolean =
        runCatching { JSONObject(text.trim()).optString("format") == FORMAT }.getOrDefault(false)

    fun decrypt(text: String, password: CharArray): List<Account> {
        try {
            val o = JSONObject(text.trim())
            require(o.getString("format") == FORMAT)
            val iterations = o.getInt("iterations")
            require(iterations in 1..5_000_000)
            val dec = Base64.getDecoder()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key(password, dec.decode(o.getString("salt")), iterations),
                GCMParameterSpec(128, dec.decode(o.getString("iv"))),
            )
            cipher.updateAAD(FORMAT.toByteArray())
            return Account.listFromJson(String(cipher.doFinal(dec.decode(o.getString("data"))), Charsets.UTF_8))
        } catch (e: Exception) {
            throw WrongPasswordOrCorrupt()
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }
}
