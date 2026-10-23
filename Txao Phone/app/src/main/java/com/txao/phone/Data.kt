package com.txao.phone

import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract

data class Contact(val id: Long, val name: String, val number: String)

data class CallEntry(val id: Long, val number: String, val name: String?, val type: Int, val date: Long, val duration: Long) {
    val missed get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
    val outgoing get() = type == CallLog.Calls.OUTGOING_TYPE
}

object Data {
    fun contacts(ctx: Context): List<Contact> {
        val out = ArrayList<Contact>()
        val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        ctx.contentResolver.query(p, cols, null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE")?.use { c ->
            while (c.moveToNext()) {
                out += Contact(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
            }
        }
        return out
    }

    fun recents(ctx: Context, limit: Int = 300): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        val cols = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
        )
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, cols, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                out += CallEntry(c.getLong(0), c.getString(1).orEmpty(), c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5))
            }
        }
        return out
    }

    fun clearRecents(ctx: Context) {
        ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
    }

    /** Name for a number from the contacts, or null. */
    fun lookupName(ctx: Context, number: String): String? {
        if (number.isEmpty()) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: SecurityException) {
            null
        }
    }
}
