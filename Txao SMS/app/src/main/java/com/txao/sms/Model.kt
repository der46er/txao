package com.txao.sms

import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

data class Conversation(
    val threadId: Long,
    val address: String,
    val name: String?,
    val snippet: String,
    val date: Long,
    val unread: Int,
    val pinned: Boolean,
) {
    val title get() = name ?: address
}

data class Msg(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val delivered: Boolean,
) {
    val outgoing get() = type != Telephony.Sms.MESSAGE_TYPE_INBOX
    val failed get() = type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val pending get() = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED
}

data class Contact(val name: String, val number: String)

data class Scheduled(val id: Long, val address: String, val body: String, val at: Long) {
    fun toJson() = JSONObject().put("id", id).put("address", address).put("body", body).put("at", at)

    companion object {
        fun fromJson(o: JSONObject) = Scheduled(o.getLong("id"), o.getString("address"), o.getString("body"), o.getLong("at"))

        fun listToJson(list: List<Scheduled>) = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()

        fun listFromJson(s: String?): List<Scheduled> {
            if (s.isNullOrBlank()) return emptyList()
            return runCatching {
                val a = JSONArray(s)
                (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }
}
