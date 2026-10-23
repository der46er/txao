package com.txao.authenticator

import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

/** Result of reading an import file: the accounts we understood and how many entries we had to skip. */
data class ImportResult(val accounts: List<Account>, val skipped: Int)

/**
 * Reads accounts from text exported by other authenticators. Supported:
 *  - one `otpauth://` / `steam://` link per line (the common plain export)
 *  - Google Authenticator "Export accounts" links (`otpauth-migration://offline?data=…`)
 *  - Aegis plain-text JSON exports (not the password-encrypted ones)
 *  - this app's own encrypted backups are handled by [Backup], not here
 */
object Importer {
    fun parse(text: String): ImportResult {
        val trimmed = text.trim().removePrefix("﻿")
        if (trimmed.startsWith("{")) return parseAegis(trimmed)
        val accounts = mutableListOf<Account>()
        var skipped = 0
        for (line in trimmed.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
            when {
                line.startsWith("otpauth-migration://", ignoreCase = true) -> {
                    val migrated = parseMigration(line)
                    if (migrated == null) skipped++ else accounts += migrated
                }
                else -> {
                    val a = Account.fromUri(line)
                    if (a == null) skipped++ else accounts += a
                }
            }
        }
        return ImportResult(accounts, skipped)
    }

    // --- Aegis plain JSON: { "db": { "entries": [ { type, name, issuer, info: { secret, algo, digits, period, counter } } ] } }
    private fun parseAegis(json: String): ImportResult {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return ImportResult(emptyList(), 1)
        val db = root.optJSONObject("db") ?: return ImportResult(emptyList(), 1) // encrypted export: db is a string
        val entries = db.optJSONArray("entries") ?: return ImportResult(emptyList(), 0)
        val out = mutableListOf<Account>()
        var skipped = 0
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i)
            val info = e?.optJSONObject("info")
            val type = when (e?.optString("type")?.lowercase()) {
                "totp" -> OtpType.TOTP
                "hotp" -> OtpType.HOTP
                "steam" -> OtpType.STEAM
                else -> null
            }
            val secret = info?.optString("secret")?.filter { it != ' ' }?.trimEnd('=')?.uppercase()
            if (e == null || info == null || type == null || secret.isNullOrEmpty() || Base32.decode(secret) == null) {
                skipped++
                continue
            }
            val digits = if (type == OtpType.STEAM) 5 else info.optInt("digits", 6)
            if (type != OtpType.STEAM && digits !in 6..8) { skipped++; continue }
            out += Account(
                issuer = e.optString("issuer"), label = e.optString("name"), secret = secret, type = type,
                algorithm = OtpAlgorithm.fromUri(info.optString("algo", "SHA1")) ?: OtpAlgorithm.SHA1,
                digits = digits, period = info.optInt("period", 30).takeIf { it > 0 } ?: 30,
                counter = info.optLong("counter", 0),
            )
        }
        return ImportResult(out, skipped)
    }

    // --- Google Authenticator migration link: a base64 protobuf. Field numbers from the public format.
    fun parseMigration(link: String): List<Account>? {
        val data = link.substringAfter("data=", "").substringBefore('&')
        if (data.isEmpty()) return null
        val bytes = runCatching {
            Base64.getMimeDecoder().decode(URLDecoder.decode(data.replace("+", "%2B"), "UTF-8").replace('-', '+').replace('_', '/'))
        }.getOrNull() ?: return null
        return runCatching {
            val out = mutableListOf<Account>()
            val top = ProtoReader(bytes)
            while (top.hasMore()) {
                val (field, wire) = top.tag()
                if (field == 1 && wire == 2) parseMigrationEntry(top.bytes())?.let { out += it } else top.skip(wire)
            }
            out
        }.getOrNull()
    }

    private fun parseMigrationEntry(bytes: ByteArray): Account? {
        val r = ProtoReader(bytes)
        var secret = ByteArray(0)
        var name = ""
        var issuer = ""
        var algo = 1
        var digits = 1
        var type = 2
        var counter = 0L
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 2 -> secret = r.bytes()
                field == 2 && wire == 2 -> name = String(r.bytes(), Charsets.UTF_8)
                field == 3 && wire == 2 -> issuer = String(r.bytes(), Charsets.UTF_8)
                field == 4 && wire == 0 -> algo = r.varint().toInt()
                field == 5 && wire == 0 -> digits = r.varint().toInt()
                field == 6 && wire == 0 -> type = r.varint().toInt()
                field == 7 && wire == 0 -> counter = r.varint()
                else -> r.skip(wire)
            }
        }
        if (secret.isEmpty()) return null
        val algorithm = when (algo) { 0, 1 -> OtpAlgorithm.SHA1; 2 -> OtpAlgorithm.SHA256; 3 -> OtpAlgorithm.SHA512; else -> return null }
        // Google puts "Issuer:account" in the name when the issuer is also set.
        val label = if (issuer.isNotEmpty() && name.startsWith("$issuer:")) name.substring(issuer.length + 1).trim() else name
        return Account(
            issuer = issuer, label = label, secret = Base32.encode(secret).trimEnd('='),
            type = if (type == 1) OtpType.HOTP else OtpType.TOTP, algorithm = algorithm,
            digits = if (digits == 2) 8 else 6, counter = counter,
        )
    }

    /** Minimal protobuf wire reader: just enough to walk the migration payload. */
    private class ProtoReader(private val b: ByteArray) {
        private var pos = 0
        fun hasMore() = pos < b.size
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val x = b[pos++].toInt()
                result = result or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
                require(shift < 64)
            }
        }
        fun tag(): Pair<Int, Int> { val t = varint().toInt(); return (t ushr 3) to (t and 7) }
        fun bytes(): ByteArray {
            val n = varint().toInt()
            require(n >= 0 && pos + n <= b.size)
            return b.copyOfRange(pos, pos + n).also { pos += n }
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> bytes()
                5 -> pos += 4
                else -> error("bad wire type")
            }
        }
    }
}
