package com.txao.authenticator

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String,
    val label: String,
    /** Base32 secret exactly as the service gave it (normalised to upper case, no spaces). */
    val secret: String,
    val type: OtpType = OtpType.TOTP,
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val counter: Long = 0,
) {
    val title: String get() = issuer.ifBlank { label }
    val subtitle: String get() = if (issuer.isBlank()) "" else label

    /** The code to show at [nowMillis]; empty if the stored secret is somehow undecodable. */
    fun code(nowMillis: Long): String {
        val key = Base32.decode(secret) ?: return ""
        return when (type) {
            OtpType.TOTP -> Otp.totp(key, nowMillis / 1000, period, digits, algorithm)
            OtpType.HOTP -> Otp.hotp(key, counter, digits, algorithm)
            OtpType.STEAM -> Otp.steam(key, nowMillis / 1000)
        }
    }

    fun toUri(): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val path = if (issuer.isBlank()) enc(label) else "${enc(issuer)}:${enc(label)}"
        val sb = StringBuilder("otpauth://${type.name.lowercase()}/$path?secret=$secret")
        if (issuer.isNotBlank()) sb.append("&issuer=").append(enc(issuer))
        if (type != OtpType.STEAM && algorithm != OtpAlgorithm.SHA1) sb.append("&algorithm=").append(algorithm.uriName)
        if (type != OtpType.STEAM && digits != 6) sb.append("&digits=").append(digits)
        if (type == OtpType.TOTP && period != 30) sb.append("&period=").append(period)
        if (type == OtpType.HOTP) sb.append("&counter=").append(counter)
        return sb.toString()
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("issuer", issuer).put("label", label).put("secret", secret)
        .put("type", type.name).put("algorithm", algorithm.name)
        .put("digits", digits).put("period", period).put("counter", counter)

    companion object {
        fun fromJson(o: JSONObject) = Account(
            id = o.getString("id"),
            issuer = o.optString("issuer"),
            label = o.optString("label"),
            secret = o.getString("secret"),
            type = runCatching { OtpType.valueOf(o.optString("type")) }.getOrDefault(OtpType.TOTP),
            algorithm = runCatching { OtpAlgorithm.valueOf(o.optString("algorithm")) }.getOrDefault(OtpAlgorithm.SHA1),
            digits = o.optInt("digits", 6),
            period = o.optInt("period", 30),
            counter = o.optLong("counter", 0),
        )

        fun listToJson(accounts: List<Account>): String =
            JSONArray().also { arr -> accounts.forEach { arr.put(it.toJson()) } }.toString()

        fun listFromJson(json: String): List<Account> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }

        /**
         * Parses the de-facto `otpauth://totp|hotp/Issuer:label?secret=…` format.
         * Returns null when the text is not a usable account.
         */
        fun fromUri(text: String): Account? {
            val s = text.trim()
            // Other apps export Steam accounts as the bare form steam://SECRET.
            if (s.startsWith("steam://", ignoreCase = true)) {
                val secret = s.substring("steam://".length).filter { it != ' ' && it != '-' }.trimEnd('=').uppercase()
                if (Base32.decode(secret) == null) return null
                return Account(issuer = "Steam", label = "", secret = secret, type = OtpType.STEAM, digits = 5)
            }
            if (!s.startsWith("otpauth://", ignoreCase = true)) return null
            val rest = s.substring("otpauth://".length)
            val slash = rest.indexOf('/')
            if (slash < 0) return null
            val type = when (rest.substring(0, slash).lowercase()) {
                "totp" -> OtpType.TOTP
                "hotp" -> OtpType.HOTP
                "steam" -> OtpType.STEAM
                else -> return null
            }
            val afterType = rest.substring(slash + 1).substringBefore('#')
            val rawPath = afterType.substringBefore('?')
            val query = if ('?' in afterType) afterType.substringAfter('?') else ""
            val params = query.split('&').filter { it.isNotEmpty() }.associate {
                dec(it.substringBefore('=')).lowercase() to dec(it.substringAfter('=', ""))
            }

            // The issuer/label separator is a literal ':'; a colon inside either part arrives as %3A.
            var issuer = ""
            var label = dec(rawPath).trim()
            val sep = rawPath.indexOf(':').takeIf { it >= 0 } ?: rawPath.indexOf("%3A", ignoreCase = true)
            if (sep >= 0) {
                val sepLen = if (rawPath[sep] == ':') 1 else 3
                issuer = dec(rawPath.substring(0, sep)).trim()
                label = dec(rawPath.substring(sep + sepLen)).trim()
            }
            params["issuer"]?.takeIf { it.isNotBlank() }?.let { issuer = it.trim() }

            val secret = params["secret"]?.filter { it != ' ' && it != '-' }?.trimEnd('=')?.uppercase() ?: return null
            if (Base32.decode(secret) == null) return null
            val algorithm = if (type == OtpType.STEAM) OtpAlgorithm.SHA1 else params["algorithm"]?.let { OtpAlgorithm.fromUri(it) ?: return null } ?: OtpAlgorithm.SHA1
            val digits = if (type == OtpType.STEAM) 5 else params["digits"]?.toIntOrNull() ?: 6
            if (type != OtpType.STEAM && digits !in 6..8) return null
            val period = if (type == OtpType.STEAM) 30 else params["period"]?.toIntOrNull() ?: 30
            if (period <= 0) return null
            val counter = params["counter"]?.toLongOrNull() ?: 0L
            if (type == OtpType.HOTP && counter < 0) return null
            return Account(
                issuer = issuer, label = label, secret = secret, type = type,
                algorithm = algorithm, digits = digits, period = period, counter = counter,
            )
        }

        private fun dec(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
    }
}
