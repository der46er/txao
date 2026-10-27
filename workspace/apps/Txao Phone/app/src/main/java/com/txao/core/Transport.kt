package com.txao.core

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class RemoteItem(val id: String, val seq: Long, val updatedAt: Long, val ciphertext: ByteArray)
class Changes(val seq: Long, val items: List<RemoteItem>, val more: Boolean)

/** What a sync engine needs from a server. Same wire contract as the Txao Notes server (SYNC-SPEC.md). */
interface Transport {
    fun changes(since: Long): Changes
    /** Returns false if the server already has a newer copy. */
    fun putItem(id: String, updatedAt: Long, ciphertext: ByteArray): Boolean
    fun hasBlob(id: String): Boolean
    fun putBlob(id: String, bytes: ByteArray)
    fun getBlob(id: String): ByteArray?
}

class SyncException(message: String) : IOException(message)

/**
 * Talks to a TXAO sync server. Requests carry only the derived login token, the optional server
 * access code and encrypted payloads. A generic User-Agent keeps device details private.
 */
class HttpTransport(
    baseUrl: String,
    private val token: String,
    private val accessCode: String,
    private val userAgent: String = "Txao",
) : Transport {
    private val base = baseUrl.trim().trimEnd('/')

    private fun open(method: String, path: String, long: Boolean = false): HttpURLConnection {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10_000
        c.readTimeout = if (long) 60_000 else 20_000
        c.useCaches = false
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", userAgent)
        c.setRequestProperty("Authorization", "Bearer $token")
        if (accessCode.isNotEmpty()) c.setRequestProperty("X-Access-Code", accessCode)
        return c
    }

    private fun check(c: HttpURLConnection, okCodes: Set<Int> = setOf(200)): Int {
        val code = c.responseCode
        if (code in okCodes) return code
        throw SyncException(
            when (code) {
                401 -> "The server rejected the access code or key"
                403 -> "This server isn't accepting new accounts"
                404 -> "Not found (check the server address)"
                413, 507 -> "The server's storage limit was reached"
                429 -> "Too many requests, try again in a minute"
                in 300..399 -> "The server redirected the request. Use the final https:// address"
                else -> "Server error ($code)"
            }
        )
    }

    override fun changes(since: Long): Changes {
        val c = open("GET", "/v1/changes?since=$since")
        try {
            check(c)
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val a = o.getJSONArray("items")
            val items = List(a.length()) {
                val x = a.getJSONObject(it)
                RemoteItem(x.getString("id"), x.getLong("seq"), x.getLong("updatedAt"), Base64.getDecoder().decode(x.getString("ciphertext")))
            }
            return Changes(o.getLong("seq"), items, o.optBoolean("more"))
        } finally { c.disconnect() }
    }

    override fun putItem(id: String, updatedAt: Long, ciphertext: ByteArray): Boolean {
        val c = open("PUT", "/v1/items/$id")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("updatedAt", updatedAt).put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            return check(c, setOf(200, 409)) == 200
        } finally { c.disconnect() }
    }

    override fun hasBlob(id: String): Boolean {
        val c = open("HEAD", "/v1/blobs/$id")
        try { return check(c, setOf(200, 404)) == 200 } finally { c.disconnect() }
    }

    override fun putBlob(id: String, bytes: ByteArray) {
        val c = open("PUT", "/v1/blobs/$id", long = true)
        try {
            c.doOutput = true
            c.setFixedLengthStreamingMode(bytes.size)
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.outputStream.use { it.write(bytes) }
            check(c)
        } finally { c.disconnect() }
    }

    override fun getBlob(id: String): ByteArray? {
        val c = open("GET", "/v1/blobs/$id", long = true)
        try {
            return if (check(c, setOf(200, 404)) == 200) c.inputStream.use { it.readBytes() } else null
        } finally { c.disconnect() }
    }

    companion object {
        fun validUrl(url: String): Boolean = runCatching {
            val u = URL(url.trim())
            (u.protocol == "https" || u.protocol == "http") && u.host.isNotEmpty()
        }.getOrDefault(false)
    }
}
