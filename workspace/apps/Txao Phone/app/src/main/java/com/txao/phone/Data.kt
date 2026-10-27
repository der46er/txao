package com.txao.phone

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone as PhoneColumns

data class Contact(val id: Long, val name: String, val number: String, val starred: Boolean, val photoUri: String? = null) {
    val lookupUri: Uri get() = ContactsContract.Contacts.getLookupUri(id, null) ?: Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString())
}

data class CallEntry(val id: Long, val number: String, val name: String?, val type: Int, val date: Long, val duration: Long, val accountId: String? = null) {
    val missed get() = type == CallLog.Calls.MISSED_TYPE
    val declined get() = type == CallLog.Calls.REJECTED_TYPE
    val blocked get() = type == CallLog.Calls.BLOCKED_TYPE
    val outgoing get() = type == CallLog.Calls.OUTGOING_TYPE
    val kind get() = when {
        missed -> "Missed"; declined -> "Declined"; blocked -> "Blocked"; outgoing -> "Outgoing"; else -> "Incoming"
    }
    val label get() = name?.takeIf { it.isNotBlank() } ?: PhoneUtil.pretty(number).ifEmpty { "Unknown" }
}

/** Consecutive calls with the same person collapsed into one row. */
data class CallGroup(val calls: List<CallEntry>) {
    val first get() = calls.first()
    val count get() = calls.size
}

object Data {
    fun contacts(ctx: Context): List<Contact> {
        val out = ArrayList<Contact>()
        val cols = arrayOf(PhoneColumns.CONTACT_ID, PhoneColumns.DISPLAY_NAME, PhoneColumns.NUMBER, PhoneColumns.STARRED, PhoneColumns.PHOTO_THUMBNAIL_URI)
        try {
            ctx.contentResolver.query(PhoneColumns.CONTENT_URI, cols, null, null, "${PhoneColumns.DISPLAY_NAME} COLLATE NOCASE")?.use { c ->
                while (c.moveToNext()) out += Contact(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3) == 1, c.getString(4))
            }
        } catch (_: SecurityException) {
        }
        // One row per contact and distinct number.
        return out.distinctBy { it.id to PhoneUtil.normalize(it.number) }
    }

    fun recents(ctx: Context, limit: Int = 400): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        val cols = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID)
        try {
            ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, cols, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    out += CallEntry(c.getLong(0), c.getString(1).orEmpty(), c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5), c.getString(6))
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }

    /** Carrier names by call-log account id, only when the phone has more than one SIM (otherwise the label is noise). */
    fun simLabels(ctx: Context): Map<String, String> = try {
        val tm = ctx.getSystemService(android.telecom.TelecomManager::class.java)
        val accounts = tm.callCapablePhoneAccounts
        if (accounts.size < 2) emptyMap()
        else accounts.associate { h -> h.id to (tm.getPhoneAccount(h)?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM") }
    } catch (_: Exception) {
        emptyMap()
    }

    fun group(calls: List<CallEntry>): List<CallGroup> {
        val out = ArrayList<MutableList<CallEntry>>()
        for (c in calls) {
            val last = out.lastOrNull()
            if (last != null && PhoneUtil.sameNumber(last.first().number, c.number)) last += c else out += mutableListOf(c)
        }
        return out.map(::CallGroup)
    }

    fun historyFor(ctx: Context, number: String): List<CallEntry> =
        recents(ctx, 2000).filter { PhoneUtil.sameNumber(it.number, number) }

    fun deleteHistory(ctx: Context, ids: List<Long>) {
        for (id in ids) ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID}=?", arrayOf(id.toString()))
    }

    fun clearRecents(ctx: Context) {
        ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
    }

    /** Numbers called most often, for the "Frequent" row in Favorites. */
    fun frequent(calls: List<CallEntry>, exclude: Set<String>, max: Int = 8): List<CallEntry> =
        calls.filter { it.outgoing && !PhoneUtil.normalize(it.number).let(exclude::contains) }
            .groupBy { PhoneUtil.key(it.number) }.values
            .sortedByDescending { it.size }.take(max).map { it.first() }

    /** Photo thumbnail for a number, or null. */
    fun lookupPhoto(ctx: Context, number: String): String? {
        if (number.isEmpty()) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: SecurityException) {
            null
        }
    }

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

    // Blocking works once Txao Phone is the default phone app.
    fun canBlock(ctx: Context): Boolean = try { BlockedNumberContract.canCurrentUserBlockNumbers(ctx) } catch (_: Exception) { false }

    fun isBlocked(ctx: Context, number: String): Boolean = try { BlockedNumberContract.isBlocked(ctx, number) } catch (_: Exception) { false }

    fun block(ctx: Context, number: String): Boolean = try {
        ctx.contentResolver.insert(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
        ) != null
    } catch (_: Exception) { false }

    fun unblock(ctx: Context, number: String): Boolean = try {
        BlockedNumberContract.unblock(ctx, number) > 0
    } catch (_: Exception) { false }

    fun blockedNumbers(ctx: Context): List<String> {
        val out = ArrayList<String>()
        try {
            ctx.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null,
            )?.use { while (it.moveToNext()) out += it.getString(0).orEmpty() }
        } catch (_: Exception) {
        }
        return out
    }
}
