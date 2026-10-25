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
    val archived: Boolean = false,
    val muted: Boolean = false,
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
    val attachments: List<Attachment> = emptyList(),
    val mms: Boolean = false,
    val subject: String? = null,
) {
    /** Text for chat lists and notifications: the message, or a label for picture messages without text. */
    val preview get() = body.ifBlank { subject ?: attachments.firstOrNull()?.label ?: if (mms) "Multimedia message" else "" }

    val outgoing get() = type != Telephony.Sms.MESSAGE_TYPE_INBOX
    val failed get() = type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val pending get() = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED
}

/** A picture, video or audio part of a multimedia message, stored by the system's MMS provider. */
data class Attachment(val partId: Long, val type: String, val name: String?) {
    val uri: android.net.Uri get() = android.net.Uri.parse("content://mms/part/$partId")
    val isImage get() = type.startsWith("image/")
    val isVideo get() = type.startsWith("video/")
    val label get() = when {
        isImage -> "Picture"
        isVideo -> "Video"
        type.startsWith("audio/") -> "Audio"
        else -> "Attachment"
    }
}

data class Contact(val name: String, val number: String)

/** What the address book (and Txao Phone) knows about a number. */
data class ContactInfo(val name: String, val contactId: Long, val photoUri: String?, val ims: List<ImEntry>)

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
