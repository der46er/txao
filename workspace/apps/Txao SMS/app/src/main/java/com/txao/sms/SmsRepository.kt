package com.txao.sms

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony

/** Reads and writes the system SMS provider (we are the default SMS app, so we own it). */
class SmsRepository(private val context: Context, private val store: Store) {
    private val resolver get() = context.contentResolver
    private class Cached(val info: ContactInfo?, val at: Long)

    private val infos = HashMap<String, Cached>()

    private val hasContacts
        get() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun conversations(): List<Conversation> {
        val addresses = canonicalAddresses()
        val unread = unreadByThread()
        val pinned = store.pinned
        val archived = store.archived
        val muted = store.muted
        val out = ArrayList<Conversation>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        resolver.query(uri, arrayOf("_id", "date", "message_count", "recipient_ids", "snippet"), null, null, "date DESC")?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(2) <= 0) continue
                val id = c.getLong(0)
                val address = c.getString(3).orEmpty().trim().split(' ')
                    .mapNotNull { addresses[it.toLongOrNull()] }.joinToString(", ")
                if (address.isEmpty()) continue
                out += Conversation(id, address, displayName(address), c.getString(4).orEmpty().ifBlank { "Multimedia message" }, c.getLong(1), unread[id] ?: 0, id in pinned, id in archived, id in muted)
            }
        }
        return out.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.date })
    }

    /** Texts and picture messages of a thread, oldest first. Picture messages carry negative ids so they never clash with texts. */
    fun messages(threadId: Long): List<Msg> =
        (query("${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "date ASC", null) +
            mmsQuery("${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), null)).sortedBy { it.date }

    fun messagesByIds(ids: Collection<Long>): List<Msg> {
        if (ids.isEmpty()) return emptyList()
        val sms = ids.filter { it > 0 }
        val mms = ids.filter { it < 0 }.map { -it }
        val texts = if (sms.isEmpty()) emptyList() else query("${Telephony.Sms._ID} IN (${sms.joinToString(",")})", emptyArray(), "date DESC", null)
        val pictures = if (mms.isEmpty()) emptyList() else mmsQuery("${Telephony.Mms._ID} IN (${mms.joinToString(",")})", emptyArray(), null)
        return (texts + pictures).sortedByDescending { it.date }
    }

    fun search(text: String): List<Msg> {
        val q = text.trim()
        if (q.length < 2) return emptyList()
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val texts = query("${Telephony.Sms.BODY} LIKE ? ESCAPE '\\'", arrayOf(like), "date DESC", 200)
        return (texts + mmsSearch(like)).sortedByDescending { it.date }.take(200)
    }

    /** Picture messages whose text part matches. */
    private fun mmsSearch(like: String): List<Msg> {
        val ids = LinkedHashSet<Long>()
        resolver.query(
            Uri.parse("content://mms/part"), arrayOf(Telephony.Mms.Part.MSG_ID),
            "${Telephony.Mms.Part.CONTENT_TYPE} = 'text/plain' AND ${Telephony.Mms.Part.TEXT} LIKE ? ESCAPE '\\'", arrayOf(like), null,
        )?.use { c -> while (c.moveToNext() && ids.size < 100) ids += c.getLong(0) }
        if (ids.isEmpty()) return emptyList()
        return mmsQuery("${Telephony.Mms._ID} IN (${ids.joinToString(",")})", emptyArray(), null)
    }

    private fun mmsQuery(where: String, args: Array<String>, limit: Int?): List<Msg> {
        val out = ArrayList<Msg>()
        val cols = arrayOf(Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.SUBJECT)
        val rows = ArrayList<LongArray>()
        val subjects = HashMap<Long, String?>()
        resolver.query(Telephony.Mms.CONTENT_URI, cols, where, args, "date DESC" + if (limit != null) " LIMIT $limit" else "")?.use { c ->
            while (c.moveToNext()) {
                rows += longArrayOf(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3))
                subjects[c.getLong(0)] = c.getString(4)
            }
        }
        for (row in rows) {
            val id = row[0]
            val box = row[3].toInt()
            var body = ""
            val attachments = ArrayList<Attachment>()
            resolver.query(
                Uri.parse("content://mms/$id/part"),
                arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT, Telephony.Mms.Part.NAME), null, null, "seq ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(1).orEmpty()
                    when {
                        type == "text/plain" -> body += (if (body.isEmpty()) "" else "\n") + c.getString(2).orEmpty()
                        type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/") -> attachments += Attachment(c.getLong(0), type, c.getString(3))
                    }
                }
            }
            val incoming = box == Telephony.Mms.MESSAGE_BOX_INBOX
            out += Msg(
                -id, row[1], Mms.peer(context, id, incoming), body, row[2] * 1000, box, false,
                attachments, mms = true, subject = subjects[id]?.takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    private fun query(where: String, args: Array<String>, order: String, limit: Int?): List<Msg> {
        val out = ArrayList<Msg>()
        val cols = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
            Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS,
        )
        val sort = if (limit != null) "$order LIMIT $limit" else order
        resolver.query(Telephony.Sms.CONTENT_URI, cols, where, args, sort)?.use { c ->
            while (c.moveToNext()) {
                out += Msg(
                    c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(),
                    c.getLong(4), c.getInt(5), c.getInt(6) == Telephony.Sms.STATUS_COMPLETE,
                )
            }
        }
        return out
    }

    private fun canonicalAddresses(): Map<Long, String> {
        val map = HashMap<Long, String>()
        resolver.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null)?.use { c ->
            while (c.moveToNext()) map[c.getLong(0)] = c.getString(1).orEmpty()
        }
        return map
    }

    private fun unreadByThread(): Map<Long, Int> {
        val map = HashMap<Long, Int>()
        resolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ?", arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()), null,
        )?.use { c -> while (c.moveToNext()) c.getLong(0).let { map[it] = (map[it] ?: 0) + 1 } }
        resolver.query(
            Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms.THREAD_ID),
            "${Telephony.Mms.READ} = 0 AND ${Telephony.Mms.MESSAGE_BOX} = ?", arrayOf(Telephony.Mms.MESSAGE_BOX_INBOX.toString()), null,
        )?.use { c -> while (c.moveToNext()) c.getLong(0).let { map[it] = (map[it] ?: 0) + 1 } }
        return map
    }

    /** Name of the contact for a number, or null. */
    fun displayName(address: String): String? = contactInfo(address)?.name

    /** What the address book knows about [address] (name, photo, usernames), or null for strangers. Cached for a minute. */
    fun contactInfo(address: String): ContactInfo? {
        if (!hasContacts || address.isBlank() || Numbers.isAlpha(address) || address.contains(',')) return null
        val now = System.currentTimeMillis()
        synchronized(infos) { infos[address]?.takeIf { now - it.at < 60_000 }?.let { return it.info } }
        val info = lookup(address)
        synchronized(infos) { infos[address] = Cached(info, now) }
        return info
    }

    /** The cached answer, without touching the database (safe on the main thread). */
    fun cachedContactInfo(address: String): ContactInfo? = synchronized(infos) { infos[address]?.info }

    private fun lookup(address: String): ContactInfo? = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        var name: String? = null
        var id = 0L
        var photo: String? = null
        resolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.CONTACT_ID, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) { name = c.getString(0); id = c.getLong(1); photo = c.getString(2) }
        }
        val n = name ?: return@runCatching null
        ContactInfo(n, id, photo, imsFor(id))
    }.getOrNull()

    /** Usernames (WhatsApp, Telegram, ...) saved on the contact, as Txao Phone stores them. */
    private fun imsFor(contactId: Long): List<ImEntry> {
        val out = ArrayList<ImEntry>()
        val d = ContactsContract.Data.CONTENT_URI
        resolver.query(
            d, arrayOf(ContactsContract.Data.DATA1, ContactsContract.Data.DATA5, ContactsContract.Data.DATA6),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val handle = c.getString(0).orEmpty()
                if (handle.isBlank()) continue
                val platform = if (c.getInt(1) == ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM) c.getString(2).orEmpty()
                else if (c.getInt(1) == ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE) "Skype" else ""
                out += ImEntry(platform, handle)
            }
        }
        return out
    }

    fun contacts(): List<Contact> {
        if (!hasContacts) return emptyList()
        val out = LinkedHashMap<String, Contact>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val number = Numbers.clean(c.getString(1).orEmpty())
                if (number.isNotEmpty()) out.getOrPut(c.getString(0).orEmpty() + number.takeLast(9)) { Contact(c.getString(0).orEmpty(), number) }
            }
        }
        return out.values.toList()
    }

    fun threadIdFor(address: String): Long = Telephony.Threads.getOrCreateThreadId(context, address)

    /** The existing thread for this address, without creating an empty one. */
    fun existingThreadId(address: String): Long? =
        conversations().firstOrNull { it.address == address || Numbers.same(it.address, address) }?.threadId

    fun insert(address: String, body: String, type: Int, read: Boolean, date: Long = System.currentTimeMillis(), threadId: Long? = null): Uri? {
        val v = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.TYPE, type)
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
            put(Telephony.Sms.THREAD_ID, threadId ?: threadIdFor(address))
        }
        return resolver.insert(Telephony.Sms.CONTENT_URI, v)
    }

    fun setType(uri: Uri, type: Int) {
        resolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null)
    }

    fun setDelivered(uri: Uri) {
        resolver.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE) }, null, null)
    }

    fun type(uri: Uri): Int? =
        resolver.query(uri, arrayOf(Telephony.Sms.TYPE), null, null, null)?.use { if (it.moveToFirst()) it.getInt(0) else null }

    fun markRead(threadId: Long) {
        val v = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
        resolver.update(Telephony.Sms.CONTENT_URI, v, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
        resolver.update(Telephony.Mms.CONTENT_URI, v, "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()))
    }

    fun deleteThread(threadId: Long) {
        resolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        resolver.delete(Telephony.Mms.CONTENT_URI, "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        store.pinned = store.pinned - threadId
        store.archived = store.archived - threadId
        store.muted = store.muted - threadId
    }

    /** Texts have positive ids, picture messages negative ones (see [messages]). */
    fun deleteMessage(id: Long) {
        if (id < 0) resolver.delete(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, -id), null, null)
        else resolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null)
    }
}
