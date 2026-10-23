package com.txao.sms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony

/** Reads and writes the system SMS provider (we are the default SMS app, so we own it). */
class SmsRepository(private val context: Context, private val store: Store) {
    private val resolver get() = context.contentResolver
    private val names = HashMap<String, String?>()

    private val hasContacts
        get() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun conversations(): List<Conversation> {
        val addresses = canonicalAddresses()
        val unread = unreadByThread()
        val pinned = store.pinned
        val out = ArrayList<Conversation>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        resolver.query(uri, arrayOf("_id", "date", "message_count", "recipient_ids", "snippet"), null, null, "date DESC")?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(2) <= 0) continue
                val id = c.getLong(0)
                val address = c.getString(3).orEmpty().trim().split(' ')
                    .mapNotNull { addresses[it.toLongOrNull()] }.joinToString(", ")
                if (address.isEmpty()) continue
                out += Conversation(id, address, displayName(address), c.getString(4).orEmpty(), c.getLong(1), unread[id] ?: 0, id in pinned)
            }
        }
        return out.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.date })
    }

    fun messages(threadId: Long): List<Msg> = query("${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "date ASC", null)

    fun search(text: String): List<Msg> {
        val q = text.trim()
        if (q.length < 2) return emptyList()
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return query("${Telephony.Sms.BODY} LIKE ? ESCAPE '\\'", arrayOf(like), "date DESC", 200)
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
        return map
    }

    /** Name of the contact for a number, or null. Cached for the repository's lifetime. */
    fun displayName(address: String): String? {
        if (!hasContacts || Numbers.isAlpha(address) || address.contains(',')) return null
        return names.getOrPut(address) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            runCatching {
                resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
        }
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
    }

    fun deleteThread(threadId: Long) {
        resolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        store.pinned = store.pinned - threadId
    }

    fun deleteMessage(id: Long) {
        resolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null)
    }
}
