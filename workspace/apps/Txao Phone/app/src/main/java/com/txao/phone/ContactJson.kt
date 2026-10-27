package com.txao.phone

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Contact <-> sync item JSON (see SYNC-SPEC.md, Txao Phone). Pure Kotlin so it can be unit tested. */
object ContactJson {
    private fun labeled(list: List<Labeled>) = JSONArray().also { a ->
        list.filter { it.value.isNotBlank() }.forEach { a.put(JSONObject().put("type", it.type).put("label", it.customLabel ?: "").put("value", it.value)) }
    }

    private fun readLabeled(a: JSONArray?): List<Labeled> =
        (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.map {
            Labeled(it.optInt("type", 2), it.optString("label").ifEmpty { null }, it.optString("value"))
        }.filter { it.value.isNotBlank() }

    /** Item body without id / updatedAt, so identical content always hashes the same. [photo] / [background] are blob ids. */
    fun body(d: ContactDetails, photo: String?, background: String?): JSONObject = JSONObject().apply {
        put("t", "contact")
        put("name", d.name)
        put("phones", labeled(d.phones))
        put("emails", labeled(d.emails))
        put("address", d.address); put("company", d.company); put("birthday", d.birthday); put("notes", d.notes)
        put("ims", JSONArray().also { a -> d.ims.filter { it.handle.isNotBlank() }.forEach { a.put(JSONObject().put("platform", it.platform).put("handle", it.handle)) } })
        put("starred", d.starred)
        if (photo != null) put("photo", photo)
        if (background != null) put("bg", background)
    }

    fun details(j: JSONObject): ContactDetails = ContactDetails(
        name = j.optString("name"),
        phones = readLabeled(j.optJSONArray("phones")),
        emails = readLabeled(j.optJSONArray("emails")),
        address = j.optString("address"), company = j.optString("company"), birthday = j.optString("birthday"), notes = j.optString("notes"),
        ims = (0 until (j.optJSONArray("ims")?.length() ?: 0)).map { j.getJSONArray("ims").getJSONObject(it) }
            .map { ImEntry(it.optString("platform"), it.optString("handle")) }.filter { it.handle.isNotBlank() },
        starred = j.optBoolean("starred"),
    )

    fun emergencyBody(name: String, number: String): JSONObject = JSONObject().put("t", "emergency").put("name", name).put("number", number)

    fun hash(body: JSONObject): String =
        MessageDigest.getInstance("SHA-256").digest(body.toString().toByteArray()).joinToString("") { "%02x".format(it) }

    fun hashBytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).take(8).joinToString("") { "%02x".format(it) }
}
